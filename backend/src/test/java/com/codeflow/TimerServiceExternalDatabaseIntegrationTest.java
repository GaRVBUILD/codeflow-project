package com.codeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "app.seed.enabled=false",
      "app.jwt.secret=external-db-test-signing-secret-0123456789",
      "app.mail.enabled=false"
    })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CODEFLOW_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
class TimerServiceExternalDatabaseIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> System.getenv("CODEFLOW_TEST_DATABASE_URL"));
    registry.add(
        "spring.datasource.username",
        () -> environmentOrDefault("CODEFLOW_TEST_DATABASE_USERNAME", "codeflow_test"));
    registry.add(
        "spring.datasource.password",
        () -> environmentOrDefault("CODEFLOW_TEST_DATABASE_PASSWORD", "codeflow_test_only"));
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private TaskRepository tasks;
  @Autowired private TimerService timers;
  @Autowired private JwtService jwt;
  @Autowired private MockMvc mockMvc;

  private AppUser user;
  private AppUser admin;
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
    admin = new AppUser();
    admin.name = "Integration Admin";
    admin.email = "integration-admin@example.test";
    admin.password = "not-a-real-password-hash";
    admin.role = "ADMIN";
    admin = users.save(admin);
    firstTask = createTask("First task");
    secondTask = createTask("Second task");
  }

  @Test
  void flywayMigrationAndTimerRoundTripWorkOnPostgres() {
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
  void concurrentStartsForOneUserLeaveOnlyOneActiveTimer() throws Exception {
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

  @Test
  void adminEndpointsRejectUserTokensAndAcceptAdminTokens() throws Exception {
    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + jwt.create(user)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/admin/users").header("Authorization", "Bearer " + jwt.create(admin)))
        .andExpect(status().isOk());
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

  private static String environmentOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
