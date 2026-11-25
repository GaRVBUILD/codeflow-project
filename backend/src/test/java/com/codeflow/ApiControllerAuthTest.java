package com.codeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class ApiControllerAuthTest {
  private UserRepository users;
  private PasswordResetRepository resets;
  private ApiController controller;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    resets = mock(PasswordResetRepository.class);
    controller =
        new ApiController(
            users,
            mock(TaskRepository.class),
            mock(TimeLogRepository.class),
            mock(CategoryRepository.class),
            mock(RuleRepository.class),
            resets,
            new TimerService(mock(TimeLogRepository.class)),
            new BCryptPasswordEncoder(),
            new JwtService("unit-test-signing-secret-0123456789abcdef"),
            mock(JavaMailSender.class),
            true,
            "no-reply@example.test",
            "http://localhost:5173");
  }

  @Test
  void registrationTrimsAndLowercasesEmailBeforeDuplicateCheckAndSave() {
    when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
    when(users.save(any(AppUser.class)))
        .thenAnswer(
            invocation -> {
              AppUser saved = invocation.getArgument(0);
              saved.id = 5L;
              return saved;
            });

    controller.register(
        new ApiController.Register("Test User", " User@Example.COM ", "password123"));

    verify(users).findByEmail("user@example.com");
    verify(users)
        .save(org.mockito.ArgumentMatchers.argThat(user -> "user@example.com".equals(user.email)));
  }

  @Test
  void loginUsesTheSameCanonicalEmail() {
    AppUser user = new AppUser();
    user.id = 5L;
    user.name = "Test User";
    user.email = "user@example.com";
    user.password = new BCryptPasswordEncoder().encode("password123");
    when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));

    controller.login(new ApiController.Login(" User@Example.COM ", "password123"));

    verify(users).findByEmail("user@example.com");
  }

  @Test
  void unknownEmailResetRequestStoresTimestampedRateLimitMarker() {
    when(resets.countByEmailAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
    when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    var response =
        controller.requestPasswordReset(
            new ApiController.PasswordResetRequest(" Missing@Example.COM "));

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    verify(resets)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                marker ->
                    marker.createdAt != null && marker.usedAt != null && marker.tokenHash == null));
  }

  @Test
  void passwordResetConfirmationLocksAndConsumesTheToken() {
    PasswordResetToken reset = new PasswordResetToken();
    reset.id = 11L;
    reset.email = "user@example.com";
    reset.createdAt = Instant.now();
    reset.expiresAt = Instant.now().plusSeconds(600);
    AppUser user = new AppUser();
    user.id = 5L;
    user.email = reset.email;
    user.password = "old-hash";
    when(resets.findByTokenHashAndUsedAtIsNullForUpdate(anyString()))
        .thenReturn(Optional.of(reset));
    when(users.findByEmail(reset.email)).thenReturn(Optional.of(user));
    when(resets.findByEmailAndUsedAtIsNull(reset.email)).thenReturn(java.util.List.of());

    var response =
        controller.confirmPasswordReset(
            new ApiController.PasswordResetConfirm("raw-reset-token", "new-password"));

    assertEquals("Password updated. You can now sign in.", response.get("message"));
    assertNotNull(reset.usedAt);
    assertEquals(1, user.tokenVersion);
    verify(resets).findByTokenHashAndUsedAtIsNullForUpdate(anyString());
    verify(resets, never()).findByTokenHashAndUsedAtIsNull(anyString());
  }

  @Test
  void resetRequestReportsUnavailableWhenMailIsDisabled() {
    ApiController noMailController =
        new ApiController(
            users,
            mock(TaskRepository.class),
            mock(TimeLogRepository.class),
            mock(CategoryRepository.class),
            mock(RuleRepository.class),
            resets,
            new TimerService(mock(TimeLogRepository.class)),
            new BCryptPasswordEncoder(),
            new JwtService("unit-test-signing-secret-0123456789abcdef"),
            mock(JavaMailSender.class),
            false,
            "no-reply@example.test",
            "http://localhost:5173");

    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class,
            () ->
                noMailController.requestPasswordReset(
                    new ApiController.PasswordResetRequest("user@example.com")));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    verify(resets, never()).save(any(PasswordResetToken.class));
  }
}
