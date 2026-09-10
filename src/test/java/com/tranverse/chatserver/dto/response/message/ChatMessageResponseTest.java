package com.tranverse.chatserver.dto.response.message;

import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.entity.Message;
import com.tranverse.chatserver.entity.MessageAttachment;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ConversationType;
import com.tranverse.chatserver.enums.MessageType;
import com.tranverse.chatserver.enums.SystemRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatMessageResponseTest {

    @Test
    void deletedMessageRetainsTimelineMetadataWithoutLeakingContentOrAttachments() {
        Conversation conversation = Conversation.create(
                "Test conversation", ConversationType.GROUP, 10, null);
        conversation.setId(UUID.randomUUID());
        User sender = User.create(
                "Test User", "test-user", "test@example.com", "hash", SystemRole.USER);
        sender.setId(UUID.randomUUID());
        Message message = Message.create(
                "sensitive deleted content", MessageType.TEXT, 42L, conversation, sender, null);
        message.setId(UUID.randomUUID());
        message.addAttachment(MessageAttachment.create(
                "https://files.example/private.png", "image/png", 128L, message));
        message.softDelete();

        ChatMessageResponse response = ChatMessageResponse.from(message);

        assertEquals(message.getId(), response.id());
        assertEquals(42L, response.sequence());
        assertNull(response.content());
        assertEquals(0, response.attachments().size());
    }
}
