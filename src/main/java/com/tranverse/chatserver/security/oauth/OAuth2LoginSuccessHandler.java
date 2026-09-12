package com.tranverse.chatserver.security.oauth;

import com.tranverse.chatserver.dto.response.auth.AuthResponse;
import com.tranverse.chatserver.service.AuthService;
import com.tranverse.chatserver.service.OAuth2ExchangeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService authService;
    private final OAuth2ExchangeService oAuth2ExchangeService;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = token.getPrincipal();

        AuthResponse authResponse = authService.loginWithGoogle(principal);
        String code = oAuth2ExchangeService.issue(authResponse);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .build().toUriString();
        response.sendRedirect(targetUrl);
    }
}
