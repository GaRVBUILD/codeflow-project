package com.codeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "app.seed.enabled=false",
      "app.jwt.secret=integration-test-signing-secret-0123456789abcdef",
      "app.mail.enabled=false"
    })
@Testcontainers(disabledWithoutDocker = true)
class TimerServiceIntegrationTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private TaskRepository tasks;
  @Autowired private TimerService timers;

  private AppUser user;
  private Task firstTask;
  private Task secondTask;

  @BeforeEach
  void prepareDatabase() {
    jdbc.update("TRUNCATE TABLE time_logs, tasks, users RESTART IDENTITY CASCADE");

    user = new AppUser();
    user.name = "Integration User";
    user.email = "integration@example.test";
    user.password = "not-a-real-password-hash";
    user = users.save(user);

    firstTask = createTask("First task");
    secondTask = createTask("Second task");
  }

  @Test
  void migrationCreatesRepositoriesAndAllowsTimerRoundTrip() {
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM prioritization_rules", Integer.class));

    TimeLog started = timers.start(user.id, firstTask);
    TimeLog stopped = timers.stop(user.id, firstTask.id);

    assertEquals(started.id, stopped.id);
    assertEquals(user.id, stopped.userId);
    assertEquals(firstTask.id, stopped.taskId);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM time_logs WHERE user_id = ? AND end_time IS NULL",
            Integer.class,
            user.id));
  }

  @Test
  void concurrentStartsForOneUserAllowOnlyOneActiveTimer() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<HttpStatus> first = pool.submit(() -> attemptStart(firstTask, ready, start));
      Future<HttpStatus> second = pool.submit(() -> attemptStart(secondTask, ready, start));
      ready.await(5, TimeUnit.SECONDS);
      start.countDown();

      HttpStatus firstResult = first.get(10, TimeUnit.SECONDS);
      HttpStatus secondResult = second.get(10, TimeUnit.SECONDS);
      long successes =
          java.util.stream.Stream.of(firstResult, secondResult)
              .filter(status -> status == HttpStatus.CREATED)
              .count();
      long conflicts =
          java.util.stream.Stream.of(firstResult, secondResult)
              .filter(status -> status == HttpStatus.CONFLICT)
              .count();

      assertEquals(1, successes);
      assertEquals(1, conflicts);
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM time_logs WHERE user_id = ? AND end_time IS NULL",
              Integer.class,
              user.id));
    } finally {
      pool.shutdownNow();
    }
  }

  private HttpStatus attemptStart(Task task, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    try {
      timers.start(user.id, task);
      return HttpStatus.CREATED;
    } catch (ResponseStatusException exception) {
      return HttpStatus.valueOf(exception.getStatusCode().value());
    }
  }

  private Task createTask(String title) {
    Task task = new Task();
    task.ownerId = user.id;
    task.title = title;
    task.importance = 3;
    task.status = "PENDING";
    task.category = "General";
    task.priorityLevel = "MEDIUM";
    return tasks.save(task);
  }
}
