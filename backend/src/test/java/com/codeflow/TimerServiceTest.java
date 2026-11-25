package com.codeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class TimerServiceTest {
  private StubTimeLogRepository timeLogs;
  private TimerService timers;

  @BeforeEach
  void setUp() {
    timeLogs = new StubTimeLogRepository();
    timers = new TimerService(timeLogs);
  }

  @Test
  void startPersistsAnActiveTimerForTheUserAndTask() {
    Task task = new Task();
    task.id = 42L;

    TimeLog started = timers.start(7L, task);

    assertEquals(7L, started.userId);
    assertEquals(42L, started.taskId);
    assertNotNull(started.startTime);
    assertNull(started.endTime);
    assertEquals(started, timeLogs.saved);
  }

  @Test
  void startRejectsASecondActiveTimerForTheSameUser() {
    timeLogs.active = new TimeLog();

    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> timers.start(7L, new Task()));

    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    assertNull(timeLogs.saved);
  }

  @Test
  void startConvertsTheDatabaseUniquenessRaceIntoAConflict() {
    timeLogs.duplicateOnSave = true;

    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> timers.start(7L, new Task()));

    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
  }

  @Test
  void stopLocksAndClosesTheMatchingActiveTimer() {
    TimeLog active = new TimeLog();
    active.id = 9L;
    active.userId = 7L;
    active.taskId = 42L;
    active.startTime = Instant.now().minusSeconds(70);
    timeLogs.active = active;

    TimeLog stopped = timers.stop(7L, 42L);

    assertNotNull(stopped.endTime);
    assertEquals(
        Duration.between(stopped.startTime, stopped.endTime).getSeconds(), stopped.durationSeconds);
    assertEquals(active, timeLogs.saved);
  }

  @Test
  void stopCannotStopAnotherTaskTimer() {
    TimeLog active = new TimeLog();
    active.taskId = 42L;
    timeLogs.active = active;

    ResponseStatusException error =
        assertThrows(ResponseStatusException.class, () -> timers.stop(7L, 43L));

    assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    assertNull(timeLogs.saved);
  }

  private static class StubTimeLogRepository extends TimeLogRepository {
    private TimeLog active;
    private TimeLog saved;
    private boolean duplicateOnSave;

    StubTimeLogRepository() {
      super((JdbcTemplate) null);
    }

    @Override
    Optional<TimeLog> findFirstByUserIdAndEndTimeIsNull(Long userId) {
      return Optional.ofNullable(active);
    }

    @Override
    Optional<TimeLog> findActiveByUserForUpdate(Long userId) {
      return Optional.ofNullable(active);
    }

    @Override
    TimeLog save(TimeLog log) {
      if (duplicateOnSave) {
        throw new DuplicateKeyException("active timer uniqueness constraint");
      }
      saved = log;
      active = log.endTime == null ? log : null;
      return log;
    }
  }
}
