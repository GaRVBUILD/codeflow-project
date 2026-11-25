package com.codeflow;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.time.LocalDateTime;

class AppUser {
  Long id;
  String name;
  String email;
  String password;
  String role = "USER";
  int tokenVersion;
  Instant createdAt = Instant.now();
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class Category {
  Long id;
  String name;
  String description;

  Category() {}

  Category(String name) {
    this.name = name;
  }
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class Task {
  Long id;
  Long ownerId;
  String title;
  String description;
  LocalDateTime deadline;
  int importance = 3;
  String status = "PENDING";
  String category = "General";
  double priorityScore;
  String priorityLevel = "MEDIUM";
  Instant createdAt = Instant.now();
  Instant updatedAt = Instant.now();
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class TimeLog {
  Long id;
  Long taskId;
  Long userId;
  Instant startTime;
  Instant endTime;
  long durationSeconds;
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
class PriorityRule {
  Long id = 1L;
  double deadlineWeight = 0.6;
  double importanceWeight = 0.4;
}

class PasswordResetToken {
  Long id;
  String email;
  String tokenHash;
  Instant createdAt = Instant.now();
  Instant expiresAt;
  Instant usedAt;
}
