-- Tasks must belong to a real account. Deleting an account removes its task tree.
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;

-- Keep the task owner pair unique so a time log cannot be attributed to a different user.
ALTER TABLE tasks
    ADD CONSTRAINT uq_tasks_id_owner UNIQUE (id, owner_id);

ALTER TABLE time_logs
    ADD CONSTRAINT fk_time_logs_task_owner
        FOREIGN KEY (task_id, user_id) REFERENCES tasks (id, owner_id) ON DELETE CASCADE;

ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_importance_range CHECK (importance BETWEEN 1 AND 5);

ALTER TABLE time_logs
    ADD CONSTRAINT ck_time_logs_duration_nonnegative CHECK (duration_seconds >= 0);
