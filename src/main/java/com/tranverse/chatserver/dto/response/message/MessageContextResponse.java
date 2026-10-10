package com.tranverse.chatserver.dto.response.message;

import java.util.List;

/** A bounded, chronological window; includes deleted-message tombstones. */
public record MessageContextResponse(
        List<ChatMessageResponse> items, boolean hasOlder, boolean hasNewer
) { }
