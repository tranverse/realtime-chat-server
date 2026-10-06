package com.tranverse.chatserver.presence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.presence")
public record PresenceProperties(long heartbeatMillis, long expiryMillis, long sweepMillis) {
    public PresenceProperties {
        if (heartbeatMillis == 0) heartbeatMillis = 25_000;
        if (expiryMillis == 0) expiryMillis = 75_000;
        if (sweepMillis == 0) sweepMillis = 5_000;
        if (heartbeatMillis < 1_000 || expiryMillis <= heartbeatMillis || sweepMillis < 100) {
            throw new IllegalArgumentException("Invalid presence timing configuration");
        }
    }
}
