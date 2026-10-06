package com.tranverse.chatserver.presence;

import java.util.UUID;

public record PresenceState(UUID userId, boolean online) {}
