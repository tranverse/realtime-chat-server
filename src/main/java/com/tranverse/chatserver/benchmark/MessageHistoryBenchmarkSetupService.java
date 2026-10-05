package com.tranverse.chatserver.benchmark;

import com.tranverse.chatserver.entity.Conversation;
import com.tranverse.chatserver.entity.ConversationMember;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ConversationMemberRole;
import com.tranverse.chatserver.enums.ConversationType;
import com.tranverse.chatserver.enums.JoinMethod;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.repository.ConversationMemberRepository;
import com.tranverse.chatserver.repository.ConversationRepository;
import com.tranverse.chatserver.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("benchmark")
public class MessageHistoryBenchmarkSetupService {

    public static final String EMAIL = "history-benchmark@example.com";
    public static final String PASSWORD = "BenchmarkPassword123!";
    private static final String DIRECT_KEY = "benchmark:message-history";

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MessageHistoryBenchmarkSetupService(UserRepository userRepository,
                                               ConversationRepository conversationRepository,
                                               ConversationMemberRepository memberRepository,
                                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void initialize() {
        User user = userRepository.findByEmail(EMAIL).orElseGet(() ->
            userRepository.save(User.create(
                    "History Benchmark",
                    "history_benchmark",
                    EMAIL,
                    passwordEncoder.encode(PASSWORD),
                    SystemRole.USER)));
        Conversation conversation = conversationRepository.findByDirectKeyAndDeletedAtIsNull(DIRECT_KEY)
                .orElseGet(() -> {
            Conversation created = Conversation.create(
                    "History Benchmark", ConversationType.GROUP, 2, null);
            created.setDirectKey(DIRECT_KEY);
            return conversationRepository.save(created);
        });
        if (memberRepository.findByConversationIdAndUserId(conversation.getId(), user.getId()).isEmpty()) {
            memberRepository.save(ConversationMember.create(
                    conversation, user, ConversationMemberRole.OWNER, JoinMethod.CREATED, null));
        }
    }

    @Transactional(readOnly = true)
    public Conversation benchmarkConversation() {
        return conversationRepository.findByDirectKeyAndDeletedAtIsNull(DIRECT_KEY)
                .orElseThrow();
    }
}
