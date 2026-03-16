package com.sollite.user.service;

import com.sollite.global.exception.BusinessException;
import com.sollite.user.exception.UserErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final TemplateEngine templateEngine;

    private static final long VERIFY_TOKEN_TTL = 30; // 30분
    private static final int RATE_LIMIT = 3;         // 10분 내 3회
    private static final long RATE_LIMIT_TTL = 10;   // 10분

    public void sendVerificationEmail(String email, Long userId) {
        checkRateLimit(email);

        String token = UUID.randomUUID().toString();

        // Redis에 인증 토큰 저장
        redisTemplate.opsForHash().putAll("email_verify:" + token, Map.of(
                "user_id", String.valueOf(userId),
                "email", email,
                "created_at", java.time.LocalDateTime.now().toString()
        ));
        redisTemplate.expire("email_verify:" + token, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        // rate limit 카운터 증가
        String rateLimitKey = "email_verify_limit:" + email;
        redisTemplate.opsForValue().increment(rateLimitKey);
        if (redisTemplate.getExpire(rateLimitKey) == -1) {
            redisTemplate.expire(rateLimitKey, RATE_LIMIT_TTL, TimeUnit.MINUTES);
        }

        sendHtmlEmail(email, token);
    }

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

    private void checkRateLimit(String email) {
        String rateLimitKey = "email_verify_limit:" + email;
        String count = redisTemplate.opsForValue().get(rateLimitKey);
        if (count != null && Integer.parseInt(count) >= RATE_LIMIT) {
            throw new BusinessException(UserErrorCode.TOO_MANY_REQUESTS);
        }
    }

    public void sendPasswordResetEmail(String email, Long userId) {
        checkRateLimit(email);

        String token = UUID.randomUUID().toString();

        redisTemplate.opsForHash().putAll("pw_reset:" + token, Map.of(
                "user_id", String.valueOf(userId),
                "email", email,
                "created_at", java.time.LocalDateTime.now().toString()
        ));
        redisTemplate.expire("pw_reset:" + token, VERIFY_TOKEN_TTL, TimeUnit.MINUTES);

        String rateLimitKey = "email_verify_limit:" + email;
        redisTemplate.opsForValue().increment(rateLimitKey);
        if (redisTemplate.getExpire(rateLimitKey) == -1) {
            redisTemplate.expire(rateLimitKey, RATE_LIMIT_TTL, TimeUnit.MINUTES);
        }

        sendPasswordResetHtmlEmail(email, token);
    }

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
            helper.setFrom("han97901@gmail.com");

            String resetUrl = "http://localhost:8080/api/auth/password/reset/confirm?token=" + token;

            Context context = new Context();
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("token", token);
            String html = templateEngine.process("password-reset", context);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    private void sendHtmlEmail(String email, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("[SOL-Lite] 이메일 인증");
            helper.setFrom("han97901@gmail.com");

            String verifyUrl = "http://localhost:8080/api/auth/email/verify/confirm?token=" + token;

            Context context = new Context();
            context.setVariable("verifyUrl", verifyUrl);
            context.setVariable("token", token);
            String html = templateEngine.process("email-verify", context);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }
}
