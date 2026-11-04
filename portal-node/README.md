# Submission Portal - AITA

This Node.js MVP provides a REST API and a browser UI for Student and Teacher workflows. It reads demo users and rules from the repository CSV files and stores new submissions in a private local JSON store.

## Run

```powershell
cd portal-node
npm.cmd install
Copy-Item .env.example .env
npm.cmd start
```

Open `http://localhost:3000`. User IDs run from `GV0001` for teachers and `HE001` for students. Usernames use each roster email's local part, and demo passwords use `hash_<username>`; duplicate email local parts include the lower-case user ID as a suffix. These are sample credentials, not production password hashes.

## Password recovery

The login screen can reset a password after matching the account's registered email and User ID. The new password must contain at least 8 characters and is stored as a scrypt hash in the local runtime data. This demo validates the email but does not send email; production recovery should use a short-lived, single-use token delivered to the account email instead of relying on a User ID.

## Implemented workflows

- Student login, semester/course grade overview, assignment notifications and viewing, source upload, SHA-256 duplicate detection, Java syntax parsing, static rule checks, and personal grades.
- Teacher course/semester grade overview, per-course grade editing and manual feedback, rule configuration, and assignment management.
- Optional OpenAI or Gemini feedback, enabled with `AI_PROVIDER=openai` or `AI_PROVIDER=gemini` and the matching API key in `.env`. The feedback prompt treats rule findings as heuristic, requires source-and-line evidence, asks the model to call out possible false positives, and prohibits invented findings or grade changes. Provider setup/response errors appear separately from the local rule results. When enabled, the submitted source code is sent to that provider; do not enable it for private code without authorization.
- The student Analysis & Fixes view shows the AST summary, parse status, AI explanation, and each rule finding with severity, line number, source evidence, and suggested fix. Rule-based score calculation remains independent of AI feedback.
- Optional Docker execution sandbox. It is disabled by default; submitted programs are never executed unless explicitly enabled and Docker is available.

## Teacher course overview

The course selector reads course codes and enrollments from `../data/students.csv` and teacher assignments from `../data/teachers.csv`. Account IDs are assigned in source CSV order and mapped to those rosters. Students are distributed evenly among all teachers assigned to each course. Since the CSV data has no per-course grades or semester field, demo grades are generated from student GPA for Fall 2026; other semester choices are empty. The portal displays imported students as active without modifying the source student/teacher CSV files. Course names are not present in the supplied data, so courses are displayed by code.

Manual feedback is stored with the selected course, student, and semester. Teachers can create, edit, and delete assignments for their courses, adjust deadlines, and see `Active` or `Expired` status based on the current time.

Students see semester and enrolled course selectors in Overview. Unread assignments produce an English notification such as `New assignment in JPE255: Homework`; opening the assignment marks it as read.

The default storage is local JSON under `storage/`; it does not modify the repository CSV data or connect to SQL Server. The JSON REST API and storage adapter can be replaced with a SQL Server repository when DB credentials/schema are ready.

## AI feedback configuration

Copy `.env.example` to `.env`, then configure exactly one provider:

```dotenv
AI_PROVIDER=openai
OPENAI_API_KEY=your-key
OPENAI_MODEL=gpt-4o-mini
```

Or set `AI_PROVIDER=gemini` with `GEMINI_API_KEY` and optionally `GEMINI_MODEL`. Use `AI_PROVIDER=disabled` to keep analysis local. AI feedback is explanatory only: it does not create findings, change severities, or affect the score. If the selected provider is misconfigured or unavailable, the local analysis is retained and a warning is shown. Provider integration tests use mocked responses; a live request requires a valid key and network access.

## Sandbox

Set `SANDBOX_ENABLED=true` and provide a pre-pulled Java image in `SANDBOX_IMAGE`. The runner uses no network, a read-only source mount, a non-root user, CPU/memory/process limits, and a short timeout. The server reports the sandbox as unavailable when Docker is missing.

## API

- `POST /api/auth/login`
- `POST /api/auth/password-reset` (`email`, `confirmationKey`, `newPassword`)
- `POST /api/auth/logout`
- `GET /api/me`
- `POST /api/student/submissions` (multipart field `sourceFile`)
- `GET /api/student/submissions`
- `GET /api/student/grades`
- `GET /api/student/course-grades`
- `GET /api/student/courses`
- `GET /api/student/assignments`
- `PATCH /api/student/assignments/:assignmentId/read`
- `GET /api/teacher/submissions`
- `GET /api/teacher/courses`
- `GET /api/teacher/course-grades`
- `PATCH /api/teacher/course-grades/:studentId`
- `PATCH /api/teacher/course-grades/:studentId/feedback`
- `GET /api/teacher/assignments`
- `POST /api/teacher/assignments`
- `PATCH /api/teacher/assignments/:assignmentId`
- `DELETE /api/teacher/assignments/:assignmentId`
- `GET /api/teacher/report`
- `GET /api/teacher/rules`
- `PATCH /api/teacher/rules/:ruleCode`
- `PATCH /api/teacher/submissions/:submissionId/grade`
- `PATCH /api/teacher/submissions/:submissionId/feedback`
- `GET /api/health`