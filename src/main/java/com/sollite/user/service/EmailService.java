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

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    private static final long VERIFY_TOKEN_TTL = 30;               // 30분
    private static final int RATE_LIMIT = 3;                       // 10분 내 3회
    private static final long RATE_LIMIT_TTL = 10;                 // 10분
    private static final String EMAIL_VERIFY_LIMIT = "email_verify_limit:";
    private static final String PW_RESET_LIMIT     = "pw_reset_limit:";

    /**
     * 이메일 인증 메일을 발송합니다.
     *
     * @param email 발송할 이메일 주소
     * @param userId 사용자 ID
     * @throws BusinessException 재발송 제한 초과 시
     * @throws BusinessException 이메일 발송 실패 시
     */
    public void sendVerificationEmail(String email, Long userId) {
        String rateLimitKey = EMAIL_VERIFY_LIMIT + email;
        checkRateLimit(rateLimitKey);

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForHash().putAll("email_verify:" + token, Map.of(
                "user_id", String.valueOf(userId),
                "email", email,
                "created_at", java.time.LocalDateTime.now().toString()
        ));
        redisTemplate.expire("email_verify:" + token, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        incrementRateLimit(rateLimitKey);
        sendHtmlEmail(email, token);
    }

    /**
     * 이메일 인증 토큰을 검증하고 사용자 정보를 반환합니다. 토큰은 1회용이므로 사용 후 삭제됩니다.
     *
     * @param token 이메일 인증 토큰
     * @return 사용자 ID와 이메일 주소
     * @throws BusinessException 토큰 만료 또는 유효하지 않은 토큰 시
     */
    public Map<String, String> verifyToken(String token) {
        String key = "email_verify:" + token;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

        if (data.isEmpty()) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }

        // 토큰 사용 후 삭제 (1회용)
        redisTemplate.delete(key);

        return Map.of(
                "user_id", (String) data.get("user_id"),
                "email", (String) data.get("email")
        );
    }

    /**
     * 이메일 발송 재요청 제한을 확인합니다. 10분 내 3회 제한입니다.
     *
     * @param email 이메일 주소
     * @throws BusinessException 재발송 제한 초과 시
     */
    private void checkRateLimit(String rateLimitKey) {
        String count = redisTemplate.opsForValue().get(rateLimitKey);
        if (count != null && Integer.parseInt(count) >= RATE_LIMIT) {
            throw new BusinessException(UserErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private void incrementRateLimit(String rateLimitKey) {
        redisTemplate.opsForValue().increment(rateLimitKey);
        if (redisTemplate.getExpire(rateLimitKey) == -1) {
            redisTemplate.expire(rateLimitKey, RATE_LIMIT_TTL, TimeUnit.MINUTES);
        }
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

        incrementRateLimit(rateLimitKey);
        sendPasswordResetHtmlEmail(email, token);
    }

    /**
     * 비밀번호 재설정 토큰을 검증하고 사용자 정보를 반환합니다. 토큰은 1회용이므로 사용 후 삭제됩니다.
     *
     * @param token 비밀번호 재설정 토큰
     * @return 사용자 ID와 이메일 주소
     * @throws BusinessException 토큰 만료 또는 유효하지 않은 토큰 시
     */
    public Map<String, String> verifyPasswordResetToken(String token) {
        String key = "pw_reset:" + token;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

        if (data.isEmpty()) {
            throw new BusinessException(UserErrorCode.TOKEN_EXPIRED);
        }

        redisTemplate.delete(key);

        return Map.of(
                "user_id", (String) data.get("user_id"),
                "email", (String) data.get("email")
        );
    }

    private void sendPasswordResetHtmlEmail(String email, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("[SOL-Lite] 비밀번호 재설정");
            helper.setFrom(mailFrom);

            String resetUrl = baseUrl + "/api/auth/password/reset/confirm?token=" + token;

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

            String verifyUrl = baseUrl + "/api/auth/email/verify/confirm?token=" + token;

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
