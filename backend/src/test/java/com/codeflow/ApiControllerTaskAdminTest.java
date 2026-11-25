package com.codeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class ApiControllerTaskAdminTest {
  private UserRepository users;
  private TaskRepository tasks;
  private RuleRepository rules;
  private ApiController controller;
  private AppUser owner;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    tasks = mock(TaskRepository.class);
    rules = mock(RuleRepository.class);
    owner = new AppUser();
    owner.id = 12L;
    owner.name = "Task Owner";
    owner.email = "owner@example.com";
    when(users.findByEmail(owner.email)).thenReturn(Optional.of(owner));
    controller =
        new ApiController(
            users,
            tasks,
            mock(TimeLogRepository.class),
            mock(CategoryRepository.class),
            rules,
            mock(PasswordResetRepository.class),
            new TimerService(mock(TimeLogRepository.class)),
            new BCryptPasswordEncoder(),
            new JwtService("unit-test-signing-secret-0123456789abcdef"),
            mock(JavaMailSender.class),
            false,
            "no-reply@example.test",
            "http://localhost:5173");
  }

  @Test
  void taskCreationSetsTheAuthenticatedOwnerAndComputesPriorityOnTheServer() {
    PriorityRule activeRules = new PriorityRule();
    when(rules.findById(1L)).thenReturn(Optional.of(activeRules));
    when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Task created =
        controller.create(
            principal(),
            new ApiController.TaskInput(
                "Prepare review", "Summarize findings", null, 5, "PENDING", "Work"));

    assertEquals(owner.id, created.ownerId);
    assertEquals("Prepare review", created.title);
    assertEquals(0.4, created.priorityScore);
    assertEquals("MEDIUM", created.priorityLevel);
    verify(tasks).save(created);
  }

  @Test
  void adminRuleUpdateRequiresWeightsToSumToOne() {
    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.setRules(new ApiController.RuleInput(0.7, 0.4)));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
  }

  @Test
  void adminRuleUpdateSavesWeightsAndRecalculatesExistingTasks() {
    PriorityRule activeRules = new PriorityRule();
    Task openTask = new Task();
    openTask.ownerId = owner.id;
    openTask.title = "Open task";
    openTask.status = "PENDING";
    Task completedTask = new Task();
    completedTask.ownerId = owner.id;
    completedTask.title = "Done task";
    completedTask.status = "COMPLETED";
    when(rules.findById(1L)).thenReturn(Optional.of(activeRules));
    when(tasks.findAll()).thenReturn(List.of(openTask, completedTask));
    when(tasks.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(rules.save(any(PriorityRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PriorityRule saved = controller.setRules(new ApiController.RuleInput(0.7, 0.3));

    assertEquals(0.7, saved.deadlineWeight);
    assertEquals(0.3, saved.importanceWeight);
    assertEquals(0.18, openTask.priorityScore);
    assertEquals(0.0, completedTask.priorityScore);
    verify(tasks).save(openTask);
    verify(tasks).save(completedTask);
  }

  private Principal principal() {
    return () -> owner.email;
  }
}
