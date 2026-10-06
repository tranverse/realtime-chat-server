package com.tranverse.chatserver.presence;

import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresenceAccess {
    private final ConversationMemberRepository members;

    public void requireAccess(UUID viewer, UUID target) {
        if (!viewer.equals(target) && !members.sharesActivePrivateConversation(viewer, target)) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION);
        }
    }
}
