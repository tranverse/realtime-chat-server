package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.response.auth.AuthResponse;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ExchangeServiceTest {
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> values;
    @InjectMocks OAuth2ExchangeService service;

    @Test
    void issueStoresTokensBehindShortLivedOpaqueCode() {
        when(redisTemplate.opsForValue()).thenReturn(values);

        String code = service.issue(new AuthResponse("access.jwt", "refresh.jwt"));

        assertFalse(code.contains("access"));
        verify(values).set(eq("oauth2:exchange:" + code), eq("access.jwt\nrefresh.jwt"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void exchangeConsumesCodeAndReturnsTokens() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.getAndDelete("oauth2:exchange:one-time-code")).thenReturn("access.jwt\nrefresh.jwt");

        AuthResponse result = service.exchange("one-time-code");

        assertEquals("access.jwt", result.getAccessToken());
        assertEquals("refresh.jwt", result.getRefreshToken());
        verify(values).getAndDelete("oauth2:exchange:one-time-code");
    }

    @Test
    void exchangeRejectsMissingOrAlreadyConsumedCode() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn(null);

        AppException exception = assertThrows(AppException.class, () -> service.exchange("expired-code"));

        assertEquals(ErrorCode.INVALID_OAUTH2_CODE, exception.getErrorCode());
    }
}
