package com.codeflow;

import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class TimerService {
  private final TimeLogRepository timeLogs;

  TimerService(TimeLogRepository timeLogs) {
    this.timeLogs = timeLogs;
  }

  @Transactional
  TimeLog start(Long userId, Task task) {
    if (timeLogs.findFirstByUserIdAndEndTimeIsNull(userId).isPresent()) {
      throw alreadyRunning();
    }

    TimeLog log = new TimeLog();
    log.taskId = task.id;
    log.userId = userId;
    log.startTime = Instant.now();
    try {
      return timeLogs.save(log);
    } catch (DuplicateKeyException exception) {
      throw alreadyRunning();
    }
  }

  @Transactional
  TimeLog stop(Long userId, Long taskId) {
    TimeLog log =
        timeLogs
            .findActiveByUserForUpdate(userId)
            .filter(active -> active.taskId.equals(taskId))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active timer"));

    log.endTime = Instant.now();
    log.durationSeconds = Duration.between(log.startTime, log.endTime).getSeconds();
    return timeLogs.save(log);
  }

  private ResponseStatusException alreadyRunning() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "Stop your active timer first");
  }
}
