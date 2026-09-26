package com.tranverse.chatserver.integration;

import com.jayway.jsonpath.JsonPath;
import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.repository.RefreshTokenRepository;
import com.tranverse.chatserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationFlowIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginPersistsSessionAndAccessTokenAuthorizesProfileRequest() throws Exception {
        userRepository.save(User.create(
                "Integration User",
                "integration_user",
                "integration@example.com",
                passwordEncoder.encode("StrongPassword123!"),
                SystemRole.USER));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "integration@example.com",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        String accessToken = JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.accessToken");

        assertEquals(1, refreshTokenRepository.count());
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("integration@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void protectedProfileRejectsRequestsWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
