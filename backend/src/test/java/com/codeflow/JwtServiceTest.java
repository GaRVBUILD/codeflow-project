package com.codeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
  @Test
  void refusesShortAndKnownPlaceholderSecrets() {
    assertThrows(IllegalStateException.class, () -> new JwtService("too-short"));
    assertThrows(
        IllegalStateException.class,
        () -> new JwtService("replace-this-with-a-32-byte-or-longer-secret"));
  }

  @Test
  void signsAndVerifiesTokensWithConfiguredSecret() {
    JwtService service = new JwtService("unit-test-signing-secret-0123456789abcdef");
    AppUser user = new AppUser();
    user.id = 13L;
    user.email = "user@example.test";
    user.role = "USER";
    user.tokenVersion = 2;

    var claims = service.parse(service.create(user));

    assertEquals(user.email, claims.getSubject());
    assertEquals(user.role, claims.get("role"));
    assertEquals(user.id.intValue(), ((Number) claims.get("uid")).intValue());
    assertEquals(user.tokenVersion, ((Number) claims.get("ver")).intValue());
  }
}
