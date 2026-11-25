CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'USER',
    token_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(4000),
    deadline TIMESTAMP WITHOUT TIME ZONE,
    importance INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    category VARCHAR(255) NOT NULL DEFAULT 'General',
    priority_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    priority_level VARCHAR(255) NOT NULL DEFAULT 'MEDIUM',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tasks_owner_id ON tasks(owner_id);
CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);

CREATE TABLE IF NOT EXISTS time_logs (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    duration_seconds BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_time_logs_task_user_start
    ON time_logs(task_id, user_id, start_time DESC);

-- Keep the most recently started timer active if older code left concurrent timers behind.
WITH ranked_active_logs AS (
    SELECT id,
           start_time,
           FIRST_VALUE(start_time) OVER (
               PARTITION BY user_id ORDER BY start_time DESC, id DESC
           ) AS newest_start_time,
           ROW_NUMBER() OVER (
               PARTITION BY user_id ORDER BY start_time DESC, id DESC
           ) AS timer_rank
    FROM time_logs
    WHERE end_time IS NULL
)
UPDATE time_logs AS timer
SET end_time = ranked.newest_start_time,
    duration_seconds = GREATEST(
            0,
            FLOOR(EXTRACT(EPOCH FROM (ranked.newest_start_time - timer.start_time)))::BIGINT
    )
FROM ranked_active_logs AS ranked
WHERE timer.id = ranked.id
  AND ranked.timer_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_time_logs_one_active_per_user
    ON time_logs(user_id)
    WHERE end_time IS NULL;

CREATE TABLE IF NOT EXISTS prioritization_rules (
    id BIGINT PRIMARY KEY,
    deadline_weight DOUBLE PRECISION NOT NULL DEFAULT 0.6,
    importance_weight DOUBLE PRECISION NOT NULL DEFAULT 0.4
);

INSERT INTO prioritization_rules (id, deadline_weight, importance_weight)
VALUES (1, 0.6, 0.4)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_password_reset_email_created
    ON password_reset_tokens(email, created_at);
