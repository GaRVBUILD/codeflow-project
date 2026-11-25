# CodeFlow

CodeFlow is a deadline and importance based task prioritization and focus tracking app. It combines a React interface with a Java 21/Spring Boot REST API and PostgreSQL.

## Features

- Personal account registration and login with BCrypt password hashing and signed JWTs.
- Forgot-password flow with single-use, 30-minute email reset links and request limiting (requires SMTP configuration).
- Private task CRUD with search, status updates, importance, category and deadlines.
- Server-side priority scores recalculated using admin configured deadline and importance weights.
- Priority board, dashboard, completion and overdue summaries, and tracked-time analytics.
- Persistent timer sessions, with one active timer per user.
- Admin area with a user activity table, category add/edit/delete, prioritization rule controls, tracked-hour and task trend charts, workload distributions, and JVM uptime/memory indicators.

## Run with Docker (recommended)

### Requirements

- Docker Desktop (Windows/macOS) or Docker Engine with the Docker Compose v2 plugin (Linux).
- Git, to clone this repository.

### Start the application

Run these commands from the repository root. Docker Compose reads the `.env` file automatically.

**PowerShell (Windows):**

```powershell
Copy-Item .env.example .env
node -e "console.log(require('crypto').randomBytes(24).toString('hex'))"
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
notepad .env
docker compose up --build -d
docker compose ps
```

**macOS/Linux:**

```bash
cp .env.example .env
# Generate two separate values and put them in DATABASE_PASSWORD and JWT_SECRET.
node -e "console.log(require('crypto').randomBytes(24).toString('hex'))"
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
# Set APP_SEED_ENABLED=true only when you explicitly want local sample data.
docker compose up --build -d
docker compose ps
```

Wait for the `db`, `backend`, and `frontend` services to start, then open:

- Web app: http://localhost:5173
- Backend API: http://localhost:8080/api

To follow service logs, run `docker compose logs -f backend frontend`. Press `Ctrl+C` to stop following logs. Stop the stack with `docker compose down`; this keeps the PostgreSQL data volume so your data is still there next time.

Compose requires non-empty `DATABASE_PASSWORD` and `JWT_SECRET` values; it has no credential fallbacks. The Compose default keeps sample seeding disabled. To create the documented demo user (`demo@codeflow.local` / `Demo1234!`) and admin (`admin@codeflow.local` / `AdminDemo123!`) for a local-only demo, set `APP_SEED_ENABLED=true` in `.env` before starting the stack. Seeded accounts use published development passwords, so never enable seeding for a public deployment.

## Run manually without Docker

### Requirements

- Java 21 and Maven.
- Node.js and npm.
- PostgreSQL 16 running locally.

### 1. Create the database

In PostgreSQL, create a database and local user. For example, run the following as a PostgreSQL administrator in `psql`:

```sql
CREATE USER codeflow WITH PASSWORD 'replace-with-a-local-password';
CREATE DATABASE codeflow OWNER codeflow;
```

### 2. Start the backend

Open a terminal at the repository root and set the connection values for that terminal. The commands below use PowerShell; replace the database password and JWT secret with your local values.

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/codeflow"
$env:DATABASE_USERNAME = "codeflow"
$env:DATABASE_PASSWORD = node -e "console.log(require('crypto').randomBytes(24).toString('hex'))"
$env:JWT_SECRET = node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
$env:CORS_ORIGIN = "http://localhost:5173"
$env:APP_SEED_ENABLED = "false"
Set-Location backend
mvn spring-boot:run
```

Keep this terminal running. Flyway applies the database migrations when the backend starts. Set `APP_SEED_ENABLED` to `true` only if you want the local development demo accounts and sample data.

### 3. Start the frontend

Open a second terminal at the repository root:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open http://localhost:5173. The frontend uses http://localhost:8080/api for the backend by default. Press `Ctrl+C` in each terminal to stop the frontend and backend.

For non-PowerShell shells, export the same environment variables before running `mvn spring-boot:run`, for example `export DATABASE_URL=jdbc:postgresql://localhost:5432/codeflow` and `export APP_SEED_ENABLED=true`.

## Stack and structure

- `backend`: Spring Web, Security, Spring JDBC, Flyway, Jakarta Validation, JWT, PostgreSQL.
- `frontend`: React, Vite, React Router, Axios, Tailwind CSS, Recharts, Lucide.
- `docker-compose.yml`: frontend, backend, and PostgreSQL services.

The API is rooted at `/api`. Authentication routes are `/auth/register`, `/auth/login`, and `/auth/me`. Authenticated task routes are `/tasks` and `/tasks/{id}`; timer routes are `/tasks/{id}/timer/start`, `/tasks/{id}/timer/stop`, and `/tasks/{id}/time-logs`. Progress is available from `/progress`, `/progress/daily`, and `/progress/weekly`. Admin routes use `/admin` and require an ADMIN JWT role: `/admin/users`, `/admin/metrics`, `/admin/categories`, and `/admin/rules`.

### Password recovery email

Password reset is disabled until an SMTP sender is configured. Copy `.env.example` to `.env`, set `MAIL_ENABLED=true`, then provide `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and a real `MAIL_FROM` address accepted by your provider. Recreate the stack with `docker compose up --build -d`. Reset links expire after 30 minutes, are single-use, are stored as hashes, and reset requests are rate-limited. Password resets invalidate existing JWT sessions. With mail disabled, the UI reports that recovery email is not configured; it never pretends an email was sent.

## Priority calculation

Importance is normalized onto 0–1 from the user supplied 1–5 rating. Deadline urgency is bounded from 0 to 1 and increases as the due time approaches; overdue tasks have urgency 1 and tasks with no deadline have urgency 0. The backend combines these values using active weights (default 60% deadline and 40% importance), assigns LOW/MEDIUM/HIGH/CRITICAL bands, and gives completed tasks a score of zero.

## Development notes

Flyway migrations manage the database schema. Seeding is disabled by default and runs only when `APP_SEED_ENABLED=true`; it creates the demo accounts, categories, tasks, rules, and time logs. Configure unique credentials and keep seeding disabled outside local development. Run backend tests with `mvn test` from `backend`. PostgreSQL integration tests use Testcontainers when Docker is available; tests named `CODEFLOW_TEST_DATABASE_*` can target a separate disposable PostgreSQL test database instead. Those integration tests truncate their test tables, so never point them at a database with data to keep.

For the PostgreSQL integration tests, set `CODEFLOW_TEST_DATABASE_URL`, `CODEFLOW_TEST_DATABASE_USERNAME`, and `CODEFLOW_TEST_DATABASE_PASSWORD` to a separate disposable PostgreSQL database before running `mvn test`. The tests verify Flyway migrations, timer concurrency, and that regular users are denied access to admin routes while admins can access them.

### Browser end-to-end check

With the Docker app running and the local demo account seeded, run the following from `frontend`. The test signs in, creates a task, verifies it appears, then deletes it. It defaults to the local demo credentials; set `CODEFLOW_E2E_EMAIL` and `CODEFLOW_E2E_PASSWORD` to use another test account. Set `CODEFLOW_WEB_URL` and `CODEFLOW_API_URL` if the app uses different local addresses.

```powershell
npm ci
npx playwright install chromium
npm run test:e2e
```
