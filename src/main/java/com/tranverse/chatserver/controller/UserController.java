package com.tranverse.chatserver.controller;

import com.tranverse.chatserver.dto.request.user.UpdateUserRequest;
import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.dto.response.PageResponse;
import com.tranverse.chatserver.dto.response.user.UserProfileResponse;
import com.tranverse.chatserver.dto.response.user.UserSummaryResponse;
import com.tranverse.chatserver.service.UserService;
import com.tranverse.chatserver.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .code(200)
                .message("Profile retrieved successfully")
                .data(userService.getProfile(SecurityUtils.userId(jwt)))
                .build());
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .code(200)
                .message("Profile updated successfully")
                .data(userService.updateProfile(SecurityUtils.userId(jwt), request))
                .build());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> searchUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") @Size(max = 120) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ApiResponse.<PageResponse<UserSummaryResponse>>builder()
                .code(200)
                .message("Users retrieved successfully")
                .data(userService.search(SecurityUtils.userId(jwt), q, page, size))
                .build());
    }
}
