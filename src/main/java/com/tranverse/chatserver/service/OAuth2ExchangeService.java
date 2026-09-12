package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.response.auth.AuthResponse;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuth2ExchangeService {
    private static final String KEY_PREFIX = "oauth2:exchange:";
    private static final Duration CODE_TTL = Duration.ofSeconds(60);
    private static final String TOKEN_SEPARATOR = "\n";

    private final StringRedisTemplate redisTemplate;

    public String issue(AuthResponse tokens) {
        String code = UUID.randomUUID().toString();
        String value = tokens.getAccessToken() + TOKEN_SEPARATOR + tokens.getRefreshToken();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, value, CODE_TTL);
        return code;
    }

    public AuthResponse exchange(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        String value = code.isEmpty() ? null : redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + code);
        if (value == null) {
            throw new AppException(ErrorCode.INVALID_OAUTH2_CODE);
        }
        String[] tokens = value.split(TOKEN_SEPARATOR, 2);
        if (tokens.length != 2 || tokens[0].isBlank() || tokens[1].isBlank()) {
            throw new AppException(ErrorCode.INVALID_OAUTH2_CODE);
        }
        return new AuthResponse(tokens[0], tokens[1]);
    }
}
