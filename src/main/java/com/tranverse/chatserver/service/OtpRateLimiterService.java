package com.tranverse.chatserver.service;

import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.enums.OtpPurpose;
import com.tranverse.chatserver.exception.AppException;
import jakarta.persistence.Lob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpRateLimiterService {
    private final StringRedisTemplate redisTemplate;

    private static final Duration SEND_WINDOW = Duration.ofMinutes(15);
    private static final Duration VERIFY_WINDOW = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);

    private static final int MAX_SEND_PER_EMAIL = 5;
    private static final int MAX_SEND_PER_IP = 20;

    private static final int MAX_VERIFY_PER_EMAIL = 5;
    private static final int MAX_VERIFY_PER_IP = 30;

    public void checkSendLimit(OtpPurpose purpose, String email, String ip) {
        String normalizeEmail = email.trim().toLowerCase();
        String ipKeyPart = safeKeyPart(normalizeIp(ip));

        String cooldownKey = "otp:cooldown:" + purpose + ":" + "email" + ":" + normalizeEmail;
        String sendEmailKey = "otp:send:" + purpose + ":email:" + normalizeEmail;
        String sendIpKey = "otp:send:" + purpose + ":ip:" + ipKeyPart;

        if(Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new AppException(ErrorCode.OTP_RESEND_TOO_FAST);
        }

        increaseAndCheck(sendEmailKey, SEND_WINDOW, MAX_SEND_PER_EMAIL, ErrorCode.OTP_SEND_TOO_MANY_REQUESTS);
        increaseAndCheck(sendIpKey, SEND_WINDOW, MAX_SEND_PER_IP, ErrorCode.OTP_SEND_TOO_MANY_REQUESTS);

        redisTemplate.opsForValue().set(cooldownKey, "1", SEND_COOLDOWN);
    }

    public void checkVerifyLimit(OtpPurpose purpose, String email, String ip) {
        String normalizeEmail = email.trim().toLowerCase();
        String ipKeyPart = safeKeyPart(normalizeIp(ip));

        String verifyEmailKey = "otp:verify:" + purpose + ":email:" + normalizeEmail;
        String verifyIpKey = "otp:verify:" + purpose + ":ip:" + ipKeyPart;

        increaseAndCheck(verifyEmailKey, VERIFY_WINDOW, MAX_VERIFY_PER_EMAIL, ErrorCode.OTP_VERIFY_TOO_MANY_ATTEMPTS);
        increaseAndCheck(verifyIpKey, VERIFY_WINDOW, MAX_VERIFY_PER_IP, ErrorCode.OTP_VERIFY_TOO_MANY_ATTEMPTS);
    }

    public void clearVerifyLimit(OtpPurpose purpose, String email, String ip) {
        String normalizeEmail = email.trim().toLowerCase();

        redisTemplate.delete("otp:verify:" + purpose + ":email:" + normalizeEmail);
    }

    private void increaseAndCheck(String key, Duration ttl, int max, ErrorCode errorCode) {
        Long count = redisTemplate.opsForValue().increment(key);

        if(count != null && count == 1){
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > max) {
            throw new AppException(errorCode);
        }

    }
    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }

        String normalized = ip.trim();

        if ("0:0:0:0:0:0:0:1".equals(normalized) || "::1".equals(normalized)) {
            return "127.0.0.1";
        }

        return normalized;
    }
    private String safeKeyPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.trim()
                .toLowerCase()
                .replace(":", "_")
                .replace("@", "_at_")
                .replace("/", "_")
                .replace("\\", "_")
                .replace(" ", "_");
    }
}
