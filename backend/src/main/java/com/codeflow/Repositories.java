package com.codeflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

final class JdbcMappers {
  private JdbcMappers() {}

  static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  static final RowMapper<AppUser> USER =
      (rs, row) -> {
        AppUser user = new AppUser();
        user.id = rs.getLong("id");
        user.name = rs.getString("name");
        user.email = rs.getString("email");
        user.password = rs.getString("password");
        user.role = rs.getString("role");
        user.tokenVersion = rs.getInt("token_version");
        user.createdAt = instant(rs, "created_at");
        return user;
      };

  static final RowMapper<Task> TASK =
      (rs, row) -> {
        Task task = new Task();
        task.id = rs.getLong("id");
        task.ownerId = rs.getLong("owner_id");
        task.title = rs.getString("title");
        task.description = rs.getString("description");
        Timestamp deadline = rs.getTimestamp("deadline");
        task.deadline = deadline == null ? null : deadline.toLocalDateTime();
        task.importance = rs.getInt("importance");
        task.status = rs.getString("status");
        task.category = rs.getString("category");
        task.priorityScore = rs.getDouble("priority_score");
        task.priorityLevel = rs.getString("priority_level");
        task.createdAt = instant(rs, "created_at");
        task.updatedAt = instant(rs, "updated_at");
        return task;
      };

  static final RowMapper<TimeLog> TIME_LOG =
      (rs, row) -> {
        TimeLog log = new TimeLog();
        log.id = rs.getLong("id");
        log.taskId = nullableLong(rs, "task_id");
        log.userId = nullableLong(rs, "user_id");
        log.startTime = instant(rs, "start_time");
        log.endTime = instant(rs, "end_time");
        log.durationSeconds = rs.getLong("duration_seconds");
        return log;
      };
}

@Repository
class UserRepository {
  private final JdbcTemplate jdbc;

  UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<AppUser> findByEmail(String email) {
    return jdbc.query("SELECT * FROM users WHERE email = ?", JdbcMappers.USER, email).stream()
        .findFirst();
  }

  List<AppUser> findAll() {
    return jdbc.query("SELECT * FROM users ORDER BY id", JdbcMappers.USER);
  }

  long count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
  }

  AppUser save(AppUser user) {
    if (user.id == null) {
      user.id =
          jdbc.queryForObject(
              "INSERT INTO users (name, email, password, role, token_version, created_at) "
                  + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
              Long.class,
              user.name,
              user.email,
              user.password,
              user.role,
              user.tokenVersion,
              Timestamp.from(user.createdAt));
    } else {
      jdbc.update(
          "UPDATE users SET name = ?, email = ?, password = ?, role = ?, "
              + "token_version = ? WHERE id = ?",
          user.name,
          user.email,
          user.password,
          user.role,
          user.tokenVersion,
          user.id);
    }
    return user;
  }
}

@Repository
class TaskRepository {
  private final JdbcTemplate jdbc;

  TaskRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Task> findById(long id) {
    return jdbc.query("SELECT * FROM tasks WHERE id = ?", JdbcMappers.TASK, id).stream()
        .findFirst();
  }

  List<Task> findByOwnerIdOrderByPriorityScoreDesc(Long ownerId) {
    return jdbc.query(
        "SELECT * FROM tasks WHERE owner_id = ? " + "ORDER BY priority_score DESC, id",
        JdbcMappers.TASK,
        ownerId);
  }

  List<Task> findAll() {
    return jdbc.query("SELECT * FROM tasks ORDER BY id", JdbcMappers.TASK);
  }

  Task save(Task task) {
    if (task.id == null) {
      task.id =
          jdbc.queryForObject(
              "INSERT INTO tasks (owner_id, title, description, deadline, importance, status,"
                  + " category, priority_score, priority_level, created_at, updated_at) VALUES (?,"
                  + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
              Long.class,
              task.ownerId,
              task.title,
              task.description,
              task.deadline == null ? null : Timestamp.valueOf(task.deadline),
              task.importance,
              task.status,
              task.category,
              task.priorityScore,
              task.priorityLevel,
              Timestamp.from(task.createdAt),
              Timestamp.from(task.updatedAt));
    } else {
      jdbc.update(
          "UPDATE tasks SET owner_id = ?, title = ?, description = ?, deadline = ?, "
              + "importance = ?, status = ?, category = ?, priority_score = ?, "
              + "priority_level = ?, updated_at = ? WHERE id = ?",
          task.ownerId,
          task.title,
          task.description,
          task.deadline == null ? null : Timestamp.valueOf(task.deadline),
          task.importance,
          task.status,
          task.category,
          task.priorityScore,
          task.priorityLevel,
          Timestamp.from(task.updatedAt),
          task.id);
    }
    return task;
  }

  void delete(Task task) {
    jdbc.update("DELETE FROM tasks WHERE id = ?", task.id);
  }
}

@Repository
class CategoryRepository {
  private final JdbcTemplate jdbc;

  CategoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<Category> CATEGORY =
      (rs, row) -> {
        Category category = new Category();
        category.id = rs.getLong("id");
        category.name = rs.getString("name");
        category.description = rs.getString("description");
        return category;
      };

  List<Category> findAll() {
    return jdbc.query("SELECT id, name, description FROM categories ORDER BY name", CATEGORY);
  }

  Optional<Category> findById(long id) {
    return jdbc
        .query("SELECT id, name, description FROM categories WHERE id = ?", CATEGORY, id)
        .stream()
        .findFirst();
  }

  long count() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM categories", Long.class);
  }

  Category save(Category category) {
    if (category.id == null) {
      category.id =
          jdbc.queryForObject(
              "INSERT INTO categories (name, description) " + "VALUES (?, ?) RETURNING id",
              Long.class,
              category.name,
              category.description);
    } else {
      jdbc.update(
          "UPDATE categories SET name = ?, description = ? WHERE id = ?",
          category.name,
          category.description,
          category.id);
    }
    return category;
  }

  void deleteById(long id) {
    jdbc.update("DELETE FROM categories WHERE id = ?", id);
  }
}

@Repository
class TimeLogRepository {
  private final JdbcTemplate jdbc;

  TimeLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<TimeLog> findByTaskIdAndUserIdOrderByStartTimeDesc(Long taskId, Long userId) {
    return jdbc.query(
        "SELECT * FROM time_logs WHERE task_id = ? AND user_id = ? "
            + "ORDER BY start_time DESC, id DESC",
        JdbcMappers.TIME_LOG,
        taskId,
        userId);
  }

  Optional<TimeLog> findFirstByUserIdAndEndTimeIsNull(Long userId) {
    return jdbc
        .query(
            "SELECT * FROM time_logs WHERE user_id = ? AND end_time IS NULL "
                + "ORDER BY start_time DESC, id DESC LIMIT 1",
            JdbcMappers.TIME_LOG,
            userId)
        .stream()
        .findFirst();
  }

  Optional<TimeLog> findActiveByUserForUpdate(Long userId) {
    return jdbc
        .query(
            "SELECT * FROM time_logs WHERE user_id = ? AND end_time IS NULL "
                + "ORDER BY start_time DESC, id DESC LIMIT 1 FOR UPDATE",
            JdbcMappers.TIME_LOG,
            userId)
        .stream()
        .findFirst();
  }

  List<TimeLog> findAll() {
    return jdbc.query("SELECT * FROM time_logs ORDER BY id", JdbcMappers.TIME_LOG);
  }

  TimeLog save(TimeLog log) {
    if (log.id == null) {
      log.id =
          jdbc.queryForObject(
              "INSERT INTO time_logs "
                  + "(task_id, user_id, start_time, end_time, duration_seconds) "
                  + "VALUES (?, ?, ?, ?, ?) RETURNING id",
              Long.class,
              log.taskId,
              log.userId,
              timestamp(log.startTime),
              timestamp(log.endTime),
              log.durationSeconds);
    } else {
      jdbc.update(
          "UPDATE time_logs SET task_id = ?, user_id = ?, start_time = ?, "
              + "end_time = ?, duration_seconds = ? WHERE id = ?",
          log.taskId,
          log.userId,
          timestamp(log.startTime),
          timestamp(log.endTime),
          log.durationSeconds,
          log.id);
    }
    return log;
  }

  private Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}

@Repository
class RuleRepository {
  private final JdbcTemplate jdbc;

  RuleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<PriorityRule> findById(long id) {
    return jdbc
        .query(
            "SELECT * FROM prioritization_rules WHERE id = ?",
            (rs, row) -> {
              PriorityRule rule = new PriorityRule();
              rule.id = rs.getLong("id");
              rule.deadlineWeight = rs.getDouble("deadline_weight");
              rule.importanceWeight = rs.getDouble("importance_weight");
              return rule;
            },
            id)
        .stream()
        .findFirst();
  }

  PriorityRule save(PriorityRule rule) {
    jdbc.update(
        "INSERT INTO prioritization_rules (id, deadline_weight, importance_weight) "
            + "VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET "
            + "deadline_weight = EXCLUDED.deadline_weight, "
            + "importance_weight = EXCLUDED.importance_weight",
        rule.id,
        rule.deadlineWeight,
        rule.importanceWeight);
    return rule;
  }
}

@Repository
class PasswordResetRepository {
  private final JdbcTemplate jdbc;

  PasswordResetRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  long countByEmailAndCreatedAtAfter(String email, Instant createdAt) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM password_reset_tokens " + "WHERE email = ? AND created_at > ?",
        Long.class,
        email,
        Timestamp.from(createdAt));
  }

  Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash) {
    return jdbc
        .query(
            "SELECT * FROM password_reset_tokens WHERE token_hash = ? " + "AND used_at IS NULL",
            (rs, row) -> map(rs),
            tokenHash)
        .stream()
        .findFirst();
  }

  Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullForUpdate(String tokenHash) {
    return jdbc
        .query(
            "SELECT * FROM password_reset_tokens WHERE token_hash = ? "
                + "AND used_at IS NULL FOR UPDATE",
            (rs, row) -> map(rs),
            tokenHash)
        .stream()
        .findFirst();
  }

  List<PasswordResetToken> findByEmailAndUsedAtIsNull(String email) {
    return jdbc.query(
        "SELECT * FROM password_reset_tokens WHERE email = ? AND used_at IS NULL",
        (rs, row) -> map(rs),
        email);
  }

  PasswordResetToken save(PasswordResetToken token) {
    if (token.id == null) {
      token.id =
          jdbc.queryForObject(
              "INSERT INTO password_reset_tokens "
                  + "(email, token_hash, created_at, expires_at, used_at) "
                  + "VALUES (?, ?, ?, ?, ?) RETURNING id",
              Long.class,
              token.email,
              token.tokenHash,
              Timestamp.from(token.createdAt),
              timestamp(token.expiresAt),
              timestamp(token.usedAt));
    } else {
      jdbc.update(
          "UPDATE password_reset_tokens SET email = ?, token_hash = ?, "
              + "created_at = ?, expires_at = ?, used_at = ? WHERE id = ?",
          token.email,
          token.tokenHash,
          Timestamp.from(token.createdAt),
          timestamp(token.expiresAt),
          timestamp(token.usedAt),
          token.id);
    }
    return token;
  }

  private PasswordResetToken map(ResultSet rs) throws SQLException {
    PasswordResetToken token = new PasswordResetToken();
    token.id = rs.getLong("id");
    token.email = rs.getString("email");
    token.tokenHash = rs.getString("token_hash");
    token.createdAt = JdbcMappers.instant(rs, "created_at");
    token.expiresAt = JdbcMappers.instant(rs, "expires_at");
    token.usedAt = JdbcMappers.instant(rs, "used_at");
    return token;
  }

  private Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
