// src/test/java/Crowdspark/Crowdspark/controller/AuthControllerTest.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.LoginResponse;
import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.service.AuthService;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import Crowdspark.Crowdspark.security.JwtUtil;
import Crowdspark.Crowdspark.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean AuthService          authService;
    @MockBean UserService          userService;
    @MockBean JwtUtil              jwtUtil;
    @MockBean RefreshTokenService  refreshTokenService;
    @MockBean EmailService         emailService;
    @MockBean JwtAuthenticationFilter jwtFilter;

    // ─── POST /auth/register ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register → 201 with valid payload")
    void register_returns201_withValidPayload() throws Exception {
        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .username("newuser")
                .email("newuser@test.com")
                .name("New User")
                .build();

        given(userService.register(any())).willReturn(userResponse);

        Map<String, String> body = Map.of(
                "name",     "New User",
                "username", "newuser",
                "email",    "newuser@test.com",
                "password", "Password123!"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.email").value("newuser@test.com"));
    }

    @Test
    @DisplayName("POST /auth/register → 400 when required field missing")
    void register_returns400_whenFieldMissing() throws Exception {
        Map<String, String> body = Map.of(
                "username", "newuser"
                // missing email, password, name
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /auth/login ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login → 200 with valid credentials")
    void login_returns200_withValidCredentials() throws Exception {
        User user = TestDataFactory.backerUser();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh_token_abc");
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        given(authService.login(anyString(), anyString())).willReturn(user);
        given(jwtUtil.generateAccessToken(any())).willReturn("access_token_xyz");
        given(refreshTokenService.create(any())).willReturn(refreshToken);

        Map<String, String> body = Map.of(
                "identifier", "testbacker",
                "password",   "Password123!"
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access_token_xyz"));
    }

    @Test
    @DisplayName("POST /auth/forgot-password → 200 always (prevents email enumeration)")
    void forgotPassword_returns200_always() throws Exception {
        Map<String, String> body = Map.of("email", "anyone@test.com");

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
