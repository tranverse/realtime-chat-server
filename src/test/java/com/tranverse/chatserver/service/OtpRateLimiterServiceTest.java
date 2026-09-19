package com.tranverse.chatserver.service;

import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.enums.OtpPurpose;
import com.tranverse.chatserver.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpRateLimiterServiceTest {
    private static final String EMAIL = "user@example.com";
    private static final String IP = "203.0.113.10";
    private static final String EMAIL_SEND_KEY = "otp:send:REGISTER:email:" + EMAIL;
    private static final String IP_SEND_KEY = "otp:send:REGISTER:ip:" + IP;
    private static final String COOLDOWN_KEY = "otp:cooldown:REGISTER:email:" + EMAIL;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> values;

    @InjectMocks
    OtpRateLimiterService service;

    @Test
    void firstRequestInitializesFifteenMinuteWindowsAndSixtySecondCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(false);
        when(values.increment(EMAIL_SEND_KEY)).thenReturn(1L);
        when(values.increment(IP_SEND_KEY)).thenReturn(1L);

        service.checkSendLimit(OtpPurpose.REGISTER, " User@Example.com ", IP);

        verify(redisTemplate).expire(EMAIL_SEND_KEY, Duration.ofMinutes(15));
        verify(redisTemplate).expire(IP_SEND_KEY, Duration.ofMinutes(15));
        verify(values).set(COOLDOWN_KEY, "1", Duration.ofSeconds(60));
    }

    @Test
    void sixthRequestForSameEmailWithinWindowIsRejected() {
        Map<String, Long> counters = new HashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(false);
        when(values.increment(anyString())).thenAnswer(invocation ->
                counters.merge(invocation.getArgument(0), 1L, Long::sum));

        for (int request = 1; request <= 5; request++) {
            assertDoesNotThrow(() -> service.checkSendLimit(OtpPurpose.REGISTER, EMAIL, IP));
        }

        AppException exception = assertThrows(
                AppException.class,
                () -> service.checkSendLimit(OtpPurpose.REGISTER, EMAIL, IP));

        assertEquals(ErrorCode.OTP_SEND_TOO_MANY_REQUESTS, exception.getErrorCode());
        assertEquals(6L, counters.get(EMAIL_SEND_KEY));
        assertEquals(5L, counters.get(IP_SEND_KEY));
    }

    @Test
    void activeCooldownRejectsRequestBeforeCountersAreIncremented() {
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.checkSendLimit(OtpPurpose.REGISTER, EMAIL, IP));

        assertEquals(ErrorCode.OTP_RESEND_TOO_FAST, exception.getErrorCode());
        verify(values, never()).increment(anyString());
    }
}
