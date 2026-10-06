package com.tranverse.chatserver.presence;

import com.tranverse.chatserver.repository.ConversationMemberRepository;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceAccessTest {
    @Test void onlySelfOrActiveDirectPeerCanReadPresence() {
        var members = mock(ConversationMemberRepository.class);
        var access = new PresenceAccess(members);
        UUID viewer = UUID.randomUUID(), peer = UUID.randomUUID();
        assertDoesNotThrow(() -> access.requireAccess(viewer, viewer));
        assertThrows(com.tranverse.chatserver.exception.AppException.class, () -> access.requireAccess(viewer, peer));
        when(members.sharesActivePrivateConversation(viewer, peer)).thenReturn(true);
        assertDoesNotThrow(() -> access.requireAccess(viewer, peer));
    }
}
