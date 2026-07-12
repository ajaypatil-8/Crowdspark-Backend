// src/test/java/Crowdspark/Crowdspark/service/AuthServiceImplTest.java
// Feature #13: AuthService is explicitly named in the feature spec
// ("JUnit 5 + Mockito tests for: AuthService, ProjectService, DonationService,
// PaymentService") but had no test file at all before this one.
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.impl.AuthServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Tests")
class AuthServiceImplTest {

    @Mock UserRepository   userRepository;
    @Mock PasswordEncoder  passwordEncoder;
    @Mock AuditLogService  auditLogService;

    @InjectMocks AuthServiceImpl authService;

    private User backer;

    // ─── login: happy paths ───────────────────────────────────────────────────

    @Test
    @DisplayName("login succeeds with username as identifier")
    void login_succeeds_withUsername() {
        backer = TestDataFactory.backerUser();
        given(userRepository.findByUsername("testbacker")).willReturn(Optional.of(backer));
        given(passwordEncoder.matches("Password123!", backer.getPassword())).willReturn(true);

        User result = authService.login("testbacker", "Password123!");

        assertThat(result.getId()).isEqualTo(backer.getId());
        verify(auditLogService).log(backer.getId(), "LOGIN_SUCCESS", "USER", backer.getId());
    }

    @Test
    @DisplayName("login falls back to email when username lookup misses")
    void login_succeeds_withEmail_whenUsernameMisses() {
        backer = TestDataFactory.backerUser();
        given(userRepository.findByUsername("backer@test.com")).willReturn(Optional.empty());
        given(userRepository.findByEmail("backer@test.com")).willReturn(Optional.of(backer));
        given(passwordEncoder.matches(any(), any())).willReturn(true);

        User result = authService.login("backer@test.com", "Password123!");

        assertThat(result.getId()).isEqualTo(backer.getId());
    }

    @Test
    @DisplayName("login falls back to phone number when username and email both miss")
    void login_succeeds_withPhoneNumber_whenUsernameAndEmailMiss() {
        backer = TestDataFactory.backerUser();
        given(userRepository.findByUsername("9876543210")).willReturn(Optional.empty());
        given(userRepository.findByEmail("9876543210")).willReturn(Optional.empty());
        given(userRepository.findByPhoneNumber("9876543210")).willReturn(Optional.of(backer));
        given(passwordEncoder.matches(any(), any())).willReturn(true);

        User result = authService.login("9876543210", "Password123!");

        assertThat(result.getId()).isEqualTo(backer.getId());
    }

    // ─── login: failure paths ─────────────────────────────────────────────────

    @Test
    @DisplayName("login throws when identifier matches no user at all")
    void login_throws_whenUserNotFound() {
        given(userRepository.findByUsername(any())).willReturn(Optional.empty());
        given(userRepository.findByEmail(any())).willReturn(Optional.empty());
        given(userRepository.findByPhoneNumber(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody", "whatever"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login throws on wrong password without leaking which field was wrong")
    void login_throws_whenPasswordWrong() {
        backer = TestDataFactory.backerUser();
        given(userRepository.findByUsername("testbacker")).willReturn(Optional.of(backer));
        given(passwordEncoder.matches("wrongpassword", backer.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login("testbacker", "wrongpassword"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("login throws for a disabled account before ever checking the password")
    void login_throws_whenAccountDisabled() {
        backer = TestDataFactory.backerUser();
        backer.setEnabled(false);
        given(userRepository.findByUsername("testbacker")).willReturn(Optional.of(backer));

        assertThatThrownBy(() -> authService.login("testbacker", "Password123!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid credentials");

        // Disabled accounts are rejected before the password is ever compared —
        // matters here because a slow/expensive check like BCrypt shouldn't run
        // for an account that's going to be rejected regardless.
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("login throws for a locked account before ever checking the password")
    void login_throws_whenAccountLocked() {
        backer = TestDataFactory.backerUser();
        backer.setLocked(true);
        given(userRepository.findByUsername("testbacker")).willReturn(Optional.of(backer));

        assertThatThrownBy(() -> authService.login("testbacker", "Password123!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid credentials");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("login does not log an audit entry when credentials are invalid")
    void login_doesNotAuditLog_onFailure() {
        given(userRepository.findByUsername(any())).willReturn(Optional.empty());
        given(userRepository.findByEmail(any())).willReturn(Optional.empty());
        given(userRepository.findByPhoneNumber(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody", "whatever"))
                .isInstanceOf(AuthException.class);

        verify(auditLogService, never()).log(any(), any(), any(), any());
    }
}
