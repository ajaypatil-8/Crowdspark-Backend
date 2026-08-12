package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.UserResponse;
import Crowdspark.Crowdspark.entity.RefreshToken;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.repository.OtpRepository;
import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.security.JwtUtil;
import Crowdspark.Crowdspark.security.filter.RateLimitFilter;
import Crowdspark.Crowdspark.service.AuthService;
import Crowdspark.Crowdspark.service.EmailService;
import Crowdspark.Crowdspark.service.RefreshTokenService;
import Crowdspark.Crowdspark.service.UserService;
import Crowdspark.Crowdspark.util.TestDataFactory;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = JwtAuthenticationFilter.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = RateLimitFilter.class
                )
        }
)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtUtil jwtUtil;

    @MockitoBean
    RefreshTokenService refreshTokenService;

    @MockitoBean
    EmailService emailService;

    @MockitoBean
    OtpRepository otpRepository;

    @MockitoBean
    PasswordEncoder passwordEncoder;

    @MockitoBean
    JpaMetamodelMappingContext jpaMappingContext;

    // ─── POST /auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register → 201 with valid payload")
    void register_returns201_withValidPayload() throws Exception {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setUsername("newuser");
        userResponse.setEmail("newuser@test.com");
        userResponse.setName("New User");

        given(userService.register(any())).willReturn(userResponse);

        Map<String, String> body = Map.of(
                "name", "New User",
                "username", "newuser",
                "email", "newuser@test.com",
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
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /auth/login ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login → 200 with valid credentials")
    void login_returns200_withValidCredentials() throws Exception {
        User user = TestDataFactory.backerUser();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh_token_abc");
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(7));

        given(authService.login(anyString(), anyString())).willReturn(user);
        given(jwtUtil.generateAccessToken(any())).willReturn("access_token_xyz");
        given(refreshTokenService.create(any())).willReturn(refreshToken);

        Map<String, String> body = Map.of(
                "identifier", "testbacker",
                "password", "Password123!"
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access_token_xyz"));
    }

    // ─── POST /auth/forgot-password ────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/forgot-password → 200 always (prevents email enumeration)")
    void forgotPassword_returns200_always() throws Exception {
        Map<String, String> body = Map.of(
                "email", "anyone@test.com"
        );

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}