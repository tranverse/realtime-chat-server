package com.tranverse.chatserver.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** All session changes, expiry pruning, and transition publication are atomic in Redis. */
@Service
@RequiredArgsConstructor
public class RedisPresenceStore {
    public static final String PREFIX = "chat:presence:";
    public static final String CHANNEL = PREFIX + "events";
    private static final String DEADLINES = PREFIX + "deadlines";
    private static final String ONLINE = PREFIX + "online";
    private static final DefaultRedisScript<Long> UPDATE = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local wasOnline = redis.call('SISMEMBER', KEYS[3], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if ARGV[2] == 'touch' then
                redis.call('ZADD', KEYS[1], now + tonumber(ARGV[4]), ARGV[3])
                redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[4]) * 2)
            elseif ARGV[2] == 'remove' then
                redis.call('ZREM', KEYS[1], ARGV[3])
            end
            local online = 0
            if redis.call('ZCARD', KEYS[1]) > 0 then
                online = 1
                redis.call('SADD', KEYS[3], ARGV[1])
                local nextExpiry = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                redis.call('ZADD', KEYS[2], nextExpiry[2], ARGV[1])
            else
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[3], ARGV[1])
                redis.call('ZREM', KEYS[2], ARGV[1])
            end
            if wasOnline ~= online then
                redis.call('PUBLISH', ARGV[5], cjson.encode({userId=ARGV[1], online=(online == 1)}))
            end
            return online
            """, Long.class);
    private static final DefaultRedisScript<List> DUE = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            return redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'LIMIT', 0, 200)
            """, List.class);
    private final StringRedisTemplate redis;
    private final PresenceProperties properties;

    public PresenceState touch(UUID userId, String sessionId) { return update(userId, "touch", sessionId); }
    public PresenceState remove(UUID userId, String sessionId) { return update(userId, "remove", sessionId); }
    public PresenceState current(UUID userId) { return update(userId, "prune", ""); }

    public void expireSessions() {
        List<?> due = redis.execute(DUE, List.of(DEADLINES));
        if (due != null) due.forEach(user -> current(UUID.fromString(user.toString())));
    }

    private PresenceState update(UUID userId, String operation, String sessionId) {
        Long online = redis.execute(UPDATE, List.of(PREFIX + "user:" + userId, DEADLINES, ONLINE),
                userId.toString(), operation, sessionId, Long.toString(properties.expiryMillis()), CHANNEL);
        return new PresenceState(userId, Long.valueOf(1).equals(online));
    }
}
