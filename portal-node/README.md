# Submission Portal - AITA

This Node.js MVP provides a REST API and a browser UI for Student and Teacher workflows. It reads demo users and rules from the repository CSV files and stores new submissions in a private local JSON store.

## Run

```powershell
cd portal-node
npm.cmd install
Copy-Item .env.example .env
npm.cmd start
```

Open `http://localhost:3000`. Demo login values are the `username` and `passwordHash` columns in `../data/users.csv`. These are sample credentials, not production password hashes.

## Implemented workflows

- Student login, source upload, SHA-256 duplicate detection, Java syntax parsing, static rule checks, analysis history, and personal grades.
- Teacher class submission list, summary report, rule configuration, grade editing, and manual feedback.
- Optional OpenAI or Gemini feedback, enabled only by setting the provider and its API key in `.env`. When enabled, the submitted source code is sent to that provider for feedback.
- Optional Docker execution sandbox. It is disabled by default; submitted programs are never executed unless explicitly enabled and Docker is available.

The default storage is local JSON under `storage/`; it does not modify the repository CSV data or connect to SQL Server. The JSON REST API and storage adapter can be replaced with a SQL Server repository when DB credentials/schema are ready.

## Sandbox

Set `SANDBOX_ENABLED=true` and provide a pre-pulled Java image in `SANDBOX_IMAGE`. The runner uses no network, a read-only source mount, a non-root user, CPU/memory/process limits, and a short timeout. The server reports the sandbox as unavailable when Docker is missing.

## API

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/me`
- `POST /api/student/submissions` (multipart field `sourceFile`)
- `GET /api/student/submissions`
- `GET /api/student/grades`
- `GET /api/teacher/submissions`
- `GET /api/teacher/report`
- `GET /api/teacher/rules`
- `PATCH /api/teacher/rules/:ruleCode`
- `PATCH /api/teacher/submissions/:submissionId/grade`
- `PATCH /api/teacher/submissions/:submissionId/feedback`
- `GET /api/health`