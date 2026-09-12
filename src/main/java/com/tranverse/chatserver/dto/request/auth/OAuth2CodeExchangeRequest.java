package com.tranverse.chatserver.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuth2CodeExchangeRequest(@NotBlank String code) {
}
