package com.tranverse.chatserver.benchmark;

import com.tranverse.chatserver.dto.response.ApiResponse;
import com.tranverse.chatserver.service.ConversationService;
import com.tranverse.chatserver.utils.SecurityUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/benchmark/message-history")
@Profile("benchmark")
@Validated
public class MessageHistoryBenchmarkController {

    private final MessageHistoryBenchmarkRepository benchmarkRepository;
    private final ConversationService conversationService;
    private final MessageHistoryBenchmarkSetupService setupService;

    public MessageHistoryBenchmarkController(MessageHistoryBenchmarkRepository benchmarkRepository,
                                             ConversationService conversationService,
                                             MessageHistoryBenchmarkSetupService setupService) {
        this.benchmarkRepository = benchmarkRepository;
        this.conversationService = conversationService;
        this.setupService = setupService;
    }

    @GetMapping("/metadata")
    public ApiResponse<Map<String, Object>> metadata(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.userId(jwt);
        UUID conversationId = setupService.benchmarkConversation().getId();
        conversationService.requireActiveMember(conversationId, userId);
        return response(Map.of("conversationId", conversationId));
    }

    @PostMapping("/{conversationId}/seed")
    public ApiResponse<Map<String, Object>> seed(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID conversationId,
                                                  @RequestParam @Min(1) @Max(500_000) int messages) {
        UUID userId = SecurityUtils.userId(jwt);
        conversationService.requireActiveMember(conversationId, userId);
        long count = benchmarkRepository.ensureMessageCount(conversationId, userId, messages);
        return response(Map.of("conversationId", conversationId, "messageCount", count));
    }

    @GetMapping("/{conversationId}/keyset")
    public ApiResponse<List<BenchmarkMessageRow>> keyset(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam long beforeSequence,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        conversationService.requireActiveMember(conversationId, SecurityUtils.userId(jwt));
        return response(benchmarkRepository.findByKeyset(conversationId, beforeSequence, size));
    }

    @GetMapping("/{conversationId}/offset")
    public ApiResponse<List<BenchmarkMessageRow>> offset(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam @Min(0) long offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        conversationService.requireActiveMember(conversationId, SecurityUtils.userId(jwt));
        return response(benchmarkRepository.findByOffset(conversationId, offset, size));
    }

    private <T> ApiResponse<T> response(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Benchmark request completed")
                .data(data)
                .build();
    }
}
