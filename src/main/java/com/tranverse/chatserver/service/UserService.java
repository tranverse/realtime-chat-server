package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.user.UpdateUserRequest;
import com.tranverse.chatserver.dto.response.PageResponse;
import com.tranverse.chatserver.dto.response.user.UserProfileResponse;
import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    public UserProfileResponse getProfile(UUID userId) {
        return UserProfileResponse.from(getActiveUser(userId));
    }

    public PageResponse<UserSummaryResponse> search(UUID currentUserId, String query, int page, int size) {
        String normalized = query == null ? "" : query.trim();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<User> users = userRepository.searchActiveUsers(
                currentUserId,
                normalized,
                PageRequest.of(safePage, safeSize)
        );
        return PageResponse.from(users, UserSummaryResponse::from);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserRequest request) {
        User user = getActiveUser(userId);

        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.username() != null) {
            String username = request.username().trim().toLowerCase(Locale.ROOT);
            if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, userId)) {
                throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
            user.setUsername(username);
        }
        if (request.avatar() != null) {
            user.setAvatar(blankToNull(request.avatar()));
        }
        if (request.phone() != null) {
            user.setPhone(blankToNull(request.phone()));
        }
        if (request.dob() != null) {
            user.setDob(request.dob());
        }

        return UserProfileResponse.from(userRepository.save(user));
    }

    public User getActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
