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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

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

    private void sendHtmlEmail(String email, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("[SOL-Lite] 이메일 인증");
            helper.setFrom("han97901@gmail.com");

            String verifyUrl = "http://localhost:8080/api/auth/email/verify/confirm?token=" + token;
            String html = buildVerificationHtml(verifyUrl, token);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    private String buildVerificationHtml(String verifyUrl, String token) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f5f5f5; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
                  <div style="max-width:600px; margin:40px auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.1);">
                    <div style="background:#0046FF; padding:32px; text-align:center;">
                      <h1 style="color:#ffffff; margin:0; font-size:24px;">SOL-Lite</h1>
                    </div>
                    <div style="padding:40px 32px;">
                      <h2 style="color:#333; margin:0 0 16px;">이메일 인증</h2>
                      <p style="color:#666; font-size:15px; line-height:1.6;">
                        SOL-Lite 회원가입을 환영합니다!<br>
                        아래 버튼을 클릭하여 이메일 인증을 완료해주세요.
                      </p>
                      <div style="text-align:center; margin:32px 0;">
                        <a href="%s" style="display:inline-block; background:#0046FF; color:#ffffff; text-decoration:none; padding:14px 40px; border-radius:8px; font-size:16px; font-weight:bold;">
                          이메일 인증하기
                        </a>
                      </div>
                      <p style="color:#999; font-size:13px; line-height:1.5;">
                        버튼이 작동하지 않으면 아래 인증 코드를 직접 입력해주세요.<br>
                        <strong style="color:#333;">%s</strong>
                      </p>
                      <hr style="border:none; border-top:1px solid #eee; margin:24px 0;">
                      <p style="color:#999; font-size:12px;">
                        이 인증 링크는 30분간 유효합니다.<br>
                        본인이 요청하지 않은 경우 이 메일을 무시해주세요.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(verifyUrl, token);
    }
}
