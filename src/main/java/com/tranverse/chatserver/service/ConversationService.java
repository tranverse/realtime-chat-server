package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.conversation.*;
import com.tranverse.chatserver.dto.response.PageResponse;
import com.tranverse.chatserver.dto.response.conversation.*;
import com.tranverse.chatserver.dto.response.message.ChatMessageResponse;
import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.entity.*;
import com.tranverse.chatserver.enums.*;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {
    private static final int DEFAULT_GROUP_LIMIT = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final ConversationInviteLinkRepository inviteLinkRepository;
    private final ConversationJoinRequestRepository joinRequestRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public PageResponse<ConversationResponse> getConversations(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Conversation> conversations = conversationRepository.findAllForUser(
                userId,
                ConversationMemberStatus.ACTIVE,
                PageRequest.of(safePage, safeSize)
        );
        return PageResponse.from(conversations, conversation -> toResponse(conversation, userId, false));
    }

    public ConversationResponse getConversation(UUID userId, UUID conversationId) {
        Conversation conversation = getActiveConversation(conversationId);
        requireActiveMember(conversationId, userId);
        return toResponse(conversation, userId, true);
    }

    @Transactional
    public ConversationResponse create(UUID userId, CreateConversationRequest request) {
        User creator = userService.getActiveUser(userId);
        Set<UUID> requestedIds = request.memberIds() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.memberIds());
        requestedIds.remove(userId);

        String directKey = null;

        if (request.type() == ConversationType.PRIVATE) {
            if (requestedIds.size() != 1) {
                throw new AppException(ErrorCode.INVALID_CONVERSATION,
                        "Private conversation requires exactly one other user");
            }
            UUID otherUserId = requestedIds.iterator().next();
            directKey = directKey(userId, otherUserId);
            Optional<Conversation> keyedConversation = conversationRepository
                    .findByDirectKeyAndDeletedAtIsNull(directKey);
            if (keyedConversation.isPresent()) {
                return toResponse(keyedConversation.get(), userId, true);
            }
            Optional<Conversation> existing = conversationRepository
                    .findActivePrivateConversation(userId, otherUserId);
            if (existing.isPresent()) {
                existing.get().setDirectKey(directKey);
                return toResponse(conversationRepository.save(existing.get()), userId, true);
            }
        } else if (request.name() == null || request.name().isBlank()) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION, "Group name is required");
        }

        int maxMembers = request.type() == ConversationType.PRIVATE
                ? 2
                : Optional.ofNullable(request.maxMembers()).orElse(DEFAULT_GROUP_LIMIT);
        if (requestedIds.size() + 1 > maxMembers) {
            throw new AppException(ErrorCode.MEMBER_LIMIT_REACHED);
        }

        List<User> invitedUsers = loadUsers(requestedIds);
        String name = request.type() == ConversationType.PRIVATE
                ? "Direct message"
                : request.name().trim();
        Conversation conversation = Conversation.create(
                name, request.type(), maxMembers, blankToNull(request.avatar()));
        conversation.setDirectKey(directKey);
        conversationRepository.save(conversation);

        ConversationMember creatorMember = ConversationMember.create(
                conversation,
                creator,
                request.type() == ConversationType.GROUP
                        ? ConversationMemberRole.OWNER
                        : ConversationMemberRole.MEMBER,
                JoinMethod.CREATED,
                null
        );
        memberRepository.save(creatorMember);

        for (User invitedUser : invitedUsers) {
            memberRepository.save(ConversationMember.create(
                    conversation,
                    invitedUser,
                    ConversationMemberRole.MEMBER,
                    JoinMethod.INVITATION,
                    creatorMember
            ));
        }

        return toResponse(conversation, userId, true);
    }

    @Transactional
    public ConversationResponse update(UUID userId,
                                       UUID conversationId,
                                       UpdateConversationRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        requireGroup(conversation);
        requireManager(conversationId, userId);

        if (request.name() != null) {
            conversation.setName(request.name().trim());
        }
        if (request.avatar() != null) {
            conversation.setAvatar(blankToNull(request.avatar()));
        }
        if (request.maxMembers() != null) {
            long currentMembers = memberRepository.countByConversationIdAndStatus(
                    conversationId, ConversationMemberStatus.ACTIVE);
            if (request.maxMembers() < currentMembers) {
                throw new AppException(ErrorCode.INVALID_CONVERSATION,
                        "Member limit cannot be lower than the current member count");
            }
            conversation.setMaxMembers(request.maxMembers());
        }
        return toResponse(conversationRepository.save(conversation), userId, true);
    }

    @Transactional
    public ConversationResponse addMembers(UUID userId,
                                           UUID conversationId,
                                           AddMembersRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        requireGroup(conversation);
        ConversationMember inviter = requireManager(conversationId, userId);

        Set<UUID> userIds = new LinkedHashSet<>(request.userIds());
        userIds.remove(userId);
        List<User> users = loadUsers(userIds);
        ensureCapacity(conversation, users.size());

        for (User user : users) {
            addOrReactivate(conversation, user, JoinMethod.INVITATION, inviter, null);
        }
        return toResponse(conversation, userId, true);
    }

    @Transactional
    public void removeMember(UUID actorUserId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = getActiveConversation(conversationId);
        requireGroup(conversation);
        ConversationMember actor = requireManager(conversationId, actorUserId);
        ConversationMember target = requireActiveMember(conversationId, targetUserId);

        if (target.getRole() == ConversationMemberRole.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION, "The owner cannot be removed");
        }
        if (actor.getRole() == ConversationMemberRole.ADMIN
                && target.getRole() == ConversationMemberRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION,
                    "An admin cannot remove another admin");
        }
        target.remove();
        memberRepository.save(target);
    }

    @Transactional
    public void leave(UUID userId, UUID conversationId) {
        Conversation conversation = getActiveConversation(conversationId);
        ConversationMember member = requireActiveMember(conversationId, userId);
        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION,
                    "A private conversation cannot be left");
        }
        if (conversation.getType() == ConversationType.GROUP
                && member.getRole() == ConversationMemberRole.OWNER) {
            throw new AppException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        member.leave();
        memberRepository.save(member);
    }

    @Transactional
    public ConversationMemberResponse updateMemberRole(UUID ownerUserId,
                                                       UUID conversationId,
                                                       UUID targetUserId,
                                                       UpdateMemberRoleRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        requireGroup(conversation);
        requireOwner(conversationId, ownerUserId);
        ConversationMember target = requireActiveMember(conversationId, targetUserId);

        if (target.getRole() == ConversationMemberRole.OWNER
                || request.role() == ConversationMemberRole.OWNER) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION,
                    "Use the ownership transfer endpoint to change the owner");
        }
        if (request.role() == ConversationMemberRole.ADMIN) {
            target.promoteToAdmin();
        } else {
            target.demoteToMember();
        }
        return ConversationMemberResponse.from(memberRepository.save(target));
    }

    @Transactional
    public ConversationResponse transferOwnership(UUID ownerUserId,
                                                  UUID conversationId,
                                                  TransferOwnershipRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        requireGroup(conversation);
        ConversationMember currentOwner = requireOwner(conversationId, ownerUserId);
        ConversationMember nextOwner = requireActiveMember(conversationId, request.userId());
        if (currentOwner.getId().equals(nextOwner.getId())) {
            return toResponse(conversation, ownerUserId, true);
        }
        currentOwner.setRole(ConversationMemberRole.ADMIN);
        nextOwner.setRole(ConversationMemberRole.OWNER);
        memberRepository.saveAll(List.of(currentOwner, nextOwner));
        return toResponse(conversation, ownerUserId, true);
    }

    @Transactional
    public InviteLinkResponse createInviteLink(UUID userId,
                                               UUID conversationId,
                                               CreateInviteLinkRequest request) {
        Conversation conversation = getActiveConversation(conversationId);
        requireGroup(conversation);
        ConversationMember creator = requireManager(conversationId, userId);
        int hours = Optional.ofNullable(request.expiresInHours()).orElse(24);
        String code = UUID.randomUUID().toString().replace("-", "");
        ConversationInviteLink link = ConversationInviteLink.create(
                code,
                request.requireApproval(),
                Instant.now().plus(hours, ChronoUnit.HOURS),
                conversation,
                creator
        );
        return InviteLinkResponse.from(inviteLinkRepository.save(link));
    }

    @Transactional
    public void revokeInviteLink(UUID userId, UUID conversationId, UUID linkId) {
        getActiveConversation(conversationId);
        requireManager(conversationId, userId);
        ConversationInviteLink link = inviteLinkRepository.findByIdAndConversationId(linkId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.INVITE_LINK_NOT_FOUND));
        link.revoke();
        inviteLinkRepository.save(link);
    }

    @Transactional
    public JoinResultResponse joinByInvite(UUID userId,
                                           String code,
                                           JoinConversationRequest request) {
        User user = userService.getActiveUser(userId);
        ConversationInviteLink link = inviteLinkRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVITE_LINK_NOT_FOUND));
        if (!link.isUsable()) {
            throw new AppException(ErrorCode.INVITE_LINK_UNAVAILABLE);
        }
        Conversation conversation = getActiveConversation(link.getConversation().getId());
        Optional<ConversationMember> existing = memberRepository
                .findByConversationIdAndUserId(conversation.getId(), userId);
        if (existing.isPresent() && existing.get().getStatus() == ConversationMemberStatus.ACTIVE) {
            return JoinResultResponse.joined(toResponse(conversation, userId, true));
        }

        if (link.isRequireApproval()) {
            if (joinRequestRepository.existsByConversationIdAndRequestedByUserIdAndStatus(
                    conversation.getId(), userId, JoinRequestStatus.PENDING)) {
                throw new AppException(ErrorCode.JOIN_REQUEST_ALREADY_EXISTS);
            }
            ConversationJoinRequest joinRequest = ConversationJoinRequest.create(
                    blankToNull(request.message()), user, conversation, link);
            return JoinResultResponse.pending(
                    JoinRequestResponse.from(joinRequestRepository.save(joinRequest)));
        }

        ensureCapacity(conversation, 1);
        addOrReactivate(conversation, user, JoinMethod.INVITE_LINK, null, null);
        return JoinResultResponse.joined(toResponse(conversation, userId, true));
    }

    public List<JoinRequestResponse> getPendingJoinRequests(UUID userId, UUID conversationId) {
        getActiveConversation(conversationId);
        requireManager(conversationId, userId);
        return joinRequestRepository
                .findAllByConversationIdAndStatusOrderByCreatedAtAsc(
                        conversationId, JoinRequestStatus.PENDING)
                .stream()
                .map(JoinRequestResponse::from)
                .toList();
    }

    @Transactional
    public JoinRequestResponse reviewJoinRequest(UUID userId,
                                                 UUID conversationId,
                                                 UUID requestId,
                                                 ReviewJoinRequest review) {
        Conversation conversation = getActiveConversation(conversationId);
        ConversationMember reviewer = requireManager(conversationId, userId);
        ConversationJoinRequest joinRequest = joinRequestRepository
                .findByIdAndConversationId(requestId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.JOIN_REQUEST_NOT_FOUND));
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION,
                    "Join request has already been reviewed");
        }

        if (Boolean.TRUE.equals(review.approved())) {
            ensureCapacity(conversation, 1);
            addOrReactivate(
                    conversation,
                    joinRequest.getRequestedByUser(),
                    JoinMethod.INVITE_LINK,
                    null,
                    reviewer
            );
            joinRequest.approve(reviewer);
        } else {
            joinRequest.reject(reviewer);
        }
        return JoinRequestResponse.from(joinRequestRepository.save(joinRequest));
    }

    public ConversationMember requireActiveMember(UUID conversationId, UUID userId) {
        return memberRepository.findByConversationIdAndUserIdAndStatus(
                        conversationId, userId, ConversationMemberStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN_CONVERSATION));
    }

    private Conversation getActiveConversation(UUID conversationId) {
        return conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private ConversationMember requireManager(UUID conversationId, UUID userId) {
        ConversationMember member = requireActiveMember(conversationId, userId);
        if (member.getRole() != ConversationMemberRole.OWNER
                && member.getRole() != ConversationMemberRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION);
        }
        return member;
    }

    private ConversationMember requireOwner(UUID conversationId, UUID userId) {
        ConversationMember member = requireActiveMember(conversationId, userId);
        if (member.getRole() != ConversationMemberRole.OWNER) {
            throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION,
                    "Only the conversation owner can perform this action");
        }
        return member;
    }

    private void requireGroup(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION,
                    "This operation is only available for group conversations");
        }
    }

    private List<User> loadUsers(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<User> users = userRepository.findAllById(userIds).stream()
                .filter(user -> !user.isDeleted())
                .toList();
        if (users.size() != userIds.size()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND,
                    "One or more users were not found");
        }
        return users;
    }

    private void ensureCapacity(Conversation conversation, int adding) {
        long current = memberRepository.countByConversationIdAndStatus(
                conversation.getId(), ConversationMemberStatus.ACTIVE);
        if (current + adding > conversation.getMaxMembers()) {
            throw new AppException(ErrorCode.MEMBER_LIMIT_REACHED);
        }
    }

    private ConversationMember addOrReactivate(Conversation conversation,
                                                User user,
                                                JoinMethod joinMethod,
                                                ConversationMember invitedBy,
                                                ConversationMember approvedBy) {
        Optional<ConversationMember> existing = memberRepository
                .findByConversationIdAndUserId(conversation.getId(), user.getId());
        ConversationMember member;
        if (existing.isPresent()) {
            member = existing.get();
            if (member.getStatus() == ConversationMemberStatus.BANNED) {
                throw new AppException(ErrorCode.FORBIDDEN_CONVERSATION,
                        "This user is banned from the conversation");
            }
            if (member.getStatus() == ConversationMemberStatus.ACTIVE) {
                throw new AppException(ErrorCode.MEMBER_ALREADY_EXISTS);
            }
            member.reactivate(ConversationMemberRole.MEMBER, joinMethod, invitedBy);
        } else {
            member = ConversationMember.create(
                    conversation, user, ConversationMemberRole.MEMBER, joinMethod, invitedBy);
        }
        member.setApprovedByMember(approvedBy);
        return memberRepository.save(member);
    }

    private ConversationResponse toResponse(Conversation conversation,
                                            UUID currentUserId,
                                            boolean includeMembers) {
        ConversationMember currentMember = requireActiveMember(conversation.getId(), currentUserId);
        List<ConversationMember> activeMembers = memberRepository
                .findAllByConversationIdAndStatusOrderByJoinedAtAsc(
                        conversation.getId(), ConversationMemberStatus.ACTIVE);
        String displayName = conversation.getName();
        String displayAvatar = conversation.getAvatar();
        if (conversation.getType() == ConversationType.PRIVATE) {
            Optional<User> other = activeMembers.stream()
                    .map(ConversationMember::getUser)
                    .filter(user -> !user.getId().equals(currentUserId))
                    .findFirst();
            if (other.isPresent()) {
                displayName = other.get().getName();
                displayAvatar = other.get().getAvatar();
            }
        }

        long lastReadSequence = currentMember.getLastReadMessage() == null
                ? 0
                : currentMember.getLastReadMessage().getSequence();
        long unreadCount = messageRepository
                .countByConversationIdAndSequenceGreaterThanAndSenderIdNotAndDeletedAtIsNull(
                        conversation.getId(), lastReadSequence, currentUserId);

        return new ConversationResponse(
                conversation.getId(),
                displayName,
                conversation.getType(),
                displayAvatar,
                conversation.getMaxMembers(),
                activeMembers.size(),
                unreadCount,
                currentMember.getRole(),
                conversation.getLastMessage() == null
                        || conversation.getLastMessage().isDeleted()
                        ? null
                        : ChatMessageResponse.from(conversation.getLastMessage()),
                includeMembers
                        ? activeMembers.stream().map(ConversationMemberResponse::from).toList()
                        : null,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String directKey(UUID firstUserId, UUID secondUserId) {
        String first = firstUserId.toString();
        String second = secondUserId.toString();
        return first.compareTo(second) < 0
                ? first + ":" + second
                : second + ":" + first;
    }
}
