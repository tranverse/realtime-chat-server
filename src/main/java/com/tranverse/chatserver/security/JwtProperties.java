package com.tranverse.chatserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String accessKey,
        String refreshKey,
        long accessExpirationMinutes,
        long refreshExpirationDays
) {
}
