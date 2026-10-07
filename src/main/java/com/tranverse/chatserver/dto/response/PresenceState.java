package com.tranverse.chatserver.dto.response;

import java.util.UUID;

public record PresenceState(UUID userId, boolean online) {}
