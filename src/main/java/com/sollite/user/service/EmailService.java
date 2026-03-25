package com.sollite.user.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.user.exception.UserErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.data.redis.core.script.RedisScript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 이메일 발송을 담당하는 서비스 클래스.
 * 이메일 인증 메일, 비밀번호 재설정 메일 발송 및 토큰 관리를 처리합니다.
 * 토큰은 Redis에 저장되고 30분 후 자동으로 만료됩니다.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final TemplateEngine templateEngine;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    private static final long VERIFY_TOKEN_TTL = 30;               // 30분
    private static final int RATE_LIMIT = 3;                       // 10분 내 3회
    private static final long RATE_LIMIT_TTL = 10;                 // 10분
    private static final String EMAIL_VERIFY_LIMIT = "email_verify_limit:";
    private static final String PW_RESET_LIMIT     = "pw_reset_limit:";
    private static final String PIN_RESET_LIMIT    = "pin_reset_limit:";

    // HGETALL + DEL 을 원자적으로 실행하는 Lua 스크립트.
    // 토큰이 존재하면 데이터를 반환하고 즉시 삭제 → 동시 요청으로 인한 1회용 토큰 중복 사용 방지
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HGETALL_AND_DEL = RedisScript.of(
            "local d = redis.call('HGETALL', KEYS[1]) " +
            "if #d == 0 then return nil end " +
            "redis.call('DEL', KEYS[1]) " +
            "return d",
            List.class);

    // 현재 카운트만 읽는 스크립트 (발송 전 초과 여부 체크용)
    // 반환: 현재 횟수 (키 없으면 0)
    private static final RedisScript<Long> CHECK_RATE_SCRIPT = RedisScript.of(
            "local c = redis.call('GET', KEYS[1]) " +
            "if c == false then return 0 end " +
            "return tonumber(c)",
            Long.class);

    // INCR + EXPIRE 원자 처리 (발송 성공 후 카운트 증가용)
    // ARGV[1] = TTL(초) — 첫 INCR 시에만 EXPIRE 설정
    private static final RedisScript<Long> INCR_RATE_SCRIPT = RedisScript.of(
            "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c",
            Long.class);

    /**
     * 이메일 인증 메일을 발송합니다. 회원가입 전에도 호출 가능합니다.
     *
     * @param email 발송할 이메일 주소
     * @throws BusinessException 재발송 제한 초과 시
     * @throws BusinessException 이메일 발송 실패 시
     */
    public String sendVerificationEmail(String email) {
        String rateLimitKey = EMAIL_VERIFY_LIMIT + email;
        checkRateLimit(rateLimitKey);

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForHash().putAll("email_verify:" + token, Map.of(
                "email", email,
                "created_at", java.time.LocalDateTime.now().toString()
        ));
        redisTemplate.expire("email_verify:" + token, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        // 폴링용 역매핑: token → email (미발급 토큰과 인증 완료 토큰 구분)
        redisTemplate.opsForValue().set("email_verify_token:" + token, email, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        sendHtmlEmail(email, token);
        incrementRateLimit(rateLimitKey);
        return token;
    }

    /**
     * 이메일 인증 토큰을 검증하고 인증 완료 상태를 Redis에 저장합니다. 토큰은 1회용이므로 사용 후 삭제됩니다.
     *
     * @param token 이메일 인증 토큰
     * @return 인증된 이메일 주소
     * @throws BusinessException 토큰 만료 또는 유효하지 않은 토큰 시
     */
    public String verifyToken(String token) {
        Map<String, String> data = getAndDeleteHash("email_verify:" + token);
        String email = data.get("email");
        redisTemplate.opsForValue().set("email_verified:" + email, "true", VERIFY_TOKEN_TTL, TimeUnit.MINUTES);
        return email;
    }

    /**
     * 이메일 발송 재요청 제한을 확인합니다. 10분 내 3회 제한입니다.
     *
     * @param email 이메일 주소
     * @throws BusinessException 재발송 제한 초과 시
     */
    private void checkRateLimit(String rateLimitKey) {
        Long count = redisTemplate.execute(CHECK_RATE_SCRIPT, List.of(rateLimitKey));
        if (count != null && count >= RATE_LIMIT) {
            throw new BusinessException(UserErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private void incrementRateLimit(String rateLimitKey) {
        redisTemplate.execute(
                INCR_RATE_SCRIPT,
                List.of(rateLimitKey),
                String.valueOf(RATE_LIMIT_TTL * 60));
    }

    /**
     * 비밀번호 재설정 메일을 발송합니다.
     *
     * @param email 발송할 이메일 주소
     * @param userId 사용자 ID
     * @throws BusinessException 재발송 제한 초과 시
     * @throws BusinessException 이메일 발송 실패 시
     */
    public void sendPasswordResetEmail(String email, Long userId) {
        String rateLimitKey = PW_RESET_LIMIT + email;
        checkRateLimit(rateLimitKey);

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForHash().putAll("pw_reset:" + token, Map.of(
                "user_id", String.valueOf(userId),
                "email", email,
                "created_at", java.time.LocalDateTime.now().toString()
        ));
        redisTemplate.expire("pw_reset:" + token, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        sendPasswordResetHtmlEmail(email, token);
        incrementRateLimit(rateLimitKey);
    }

    /**
     * 비밀번호 재설정 토큰을 검증하고 사용자 정보를 반환합니다. 토큰은 1회용이므로 사용 후 삭제됩니다.
     *
     * @param token 비밀번호 재설정 토큰
     * @return 사용자 ID와 이메일 주소
     * @throws BusinessException 토큰 만료 또는 유효하지 않은 토큰 시
     */
    public Map<String, String> verifyPasswordResetToken(String token) {
        Map<String, String> data = getAndDeleteHash("pw_reset:" + token);
        return Map.of(
                "user_id", getRequiredTokenField(data, "user_id"),
                "email", getRequiredTokenField(data, "email")
        );
    }

    public void sendPinResetEmail(String email, Long userId) {
        String rateLimitKey = PIN_RESET_LIMIT + email;
        checkRateLimit(rateLimitKey);

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForHash().putAll("pin_reset:" + token, Map.of(
                "user_id", String.valueOf(userId),
                "email", email,
                "created_at", java.time.LocalDateTime.now().toString()
        ));
        redisTemplate.expire("pin_reset:" + token, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        sendPinResetHtmlEmail(email, token);
        incrementRateLimit(rateLimitKey);
    }

    public Map<String, String> verifyPinResetToken(String token) {
        Map<String, String> data = getAndDeleteHash("pin_reset:" + token);
        return Map.of(
                "user_id", getRequiredTokenField(data, "user_id"),
                "email", getRequiredTokenField(data, "email")
        );
    }

    private String getRequiredTokenField(Map<String, String> data, String fieldName) {
        String value = data.get(fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getAndDeleteHash(String key) {
        List<String> raw = (List<String>) redisTemplate.execute(HGETALL_AND_DEL, List.of(key));
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            result.put(raw.get(i), raw.get(i + 1));
        }
        return result;
    }

    private void sendPinResetHtmlEmail(String email, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("[SOL-Lite] 계좌 비밀번호 재설정");
            helper.setFrom(mailFrom);

            String resetUrl = frontendUrl + "/account/pin/reset?token=" + token;

            Context context = new Context();
            context.setVariable("resetUrl", resetUrl);
            String html = templateEngine.process("pin-reset", context);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new BusinessException(UserErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private void sendPasswordResetHtmlEmail(String email, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("[SOL-Lite] 비밀번호 재설정");
            helper.setFrom(mailFrom);

            String resetUrl = frontendUrl + "/password/reset?token=" + token;

            Context context = new Context();
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("token", token);
            String html = templateEngine.process("password-reset", context);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new BusinessException(UserErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private void sendHtmlEmail(String email, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("[SOL-Lite] 이메일 인증");
            helper.setFrom(mailFrom);

            String verifyUrl = frontendUrl + "/email/verify?token=" + token;

            Context context = new Context();
            context.setVariable("verifyUrl", verifyUrl);
            context.setVariable("token", token);
            String html = templateEngine.process("email-verify", context);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new BusinessException(UserErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
