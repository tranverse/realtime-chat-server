package com.tranverse.chatserver.service;

import com.tranverse.chatserver.dto.request.auth.LoginRequest;
import com.tranverse.chatserver.dto.response.auth.LoginResponse;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.UserRepository;
import com.tranverse.chatserver.security.jwt.JwtService;
import com.tranverse.chatserver.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.id()).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        String accessToken = jwtService.generateAccessToken(principal.id().toString(), principal.role());
        String refreshToken = jwtService.generateRefreshToken(principal.id().toString());

        return new LoginResponse(accessToken, refreshToken);
    }

    public
}
