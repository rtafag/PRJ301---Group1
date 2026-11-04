# Submission Portal - AITA

This Node.js MVP provides a REST API and a browser UI for Student and Teacher workflows. It reads demo users and rules from the repository CSV files and stores new submissions in a private local JSON store.

## Redis analysis queue

Java submissions are persisted with `Queued` status and analyzed asynchronously by a Redis-backed priority queue. Assignments due within 24 hours receive priority 1, assignments due within 7 days receive priority 5, and other submissions receive priority 10 (lower numbers run first). Failed analysis is retried up to three attempts with exponential backoff, capped at 30 seconds. Exhausted jobs are marked `Analysis Failed`.

Install and start Redis before starting the portal, then set `REDIS_URL` in `.env` (for example, `redis://127.0.0.1:6379`). The queue uses Redis directly and adds no npm dependencies. If Redis is unavailable, the upload is retained with `Queue Failed` status and the API returns an error rather than silently analyzing inline.

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

- Student login, semester/course grade overview, persistent deadline/assignment/grade/feedback notifications, profile and password settings, source upload, SHA-256 duplicate detection, Java syntax parsing, static rule checks, and personal grades.
- Teacher course/semester grade overview, editable class enrollments and teacher assignments, per-course grade editing and manual feedback, rule configuration, assignment management, submission review, plagiarism signals, grade audit history, reports, and CSV/XLSX export.
- Optional OpenAI or Gemini feedback, enabled with `AI_PROVIDER=openai` or `AI_PROVIDER=gemini` and the matching API key in `.env`. The feedback prompt treats rule findings as heuristic, requires source-and-line evidence, asks the model to call out possible false positives, and prohibits invented findings or grade changes. Provider setup/response errors appear separately from the local rule results. When enabled, the submitted source code is sent to that provider; do not enable it for private code without authorization.
- The student Analysis & Fixes view shows the AST summary, parse status, AI explanation, and each rule finding with severity, line number, source evidence, and suggested fix. Rule-based score calculation remains independent of AI feedback.
- Optional Docker execution sandbox. It is disabled by default; submitted programs are never executed unless explicitly enabled and Docker is available.

## Teacher course overview

The course selector seeds course codes and enrollments from `../data/students.csv` and teacher assignments from `../data/teachers.csv`. Account IDs are mapped in source CSV order and initial students are distributed evenly among assigned teachers. Changes to enrollments and course-teacher assignments are stored separately in `storage/portal-data.json`; they never rewrite the source CSV files. Since the CSV data has no per-course grades or semester field, demo grades are generated from student GPA for Fall 2026; other semester choices are empty. Course names are not present in the supplied data, so courses are displayed by code.

Manual feedback is stored with the selected course, student, and semester. Teachers can create, edit, and delete assignments for their courses, adjust deadlines, and see `Active` or `Expired` status based on the current time.

Students see semester and enrolled course selectors in Overview. The notification inbox includes upcoming deadlines (within 48 hours), new assignments, updated grades, and new feedback, with persistent read state. Account settings allow users to update their name/email and change their password after verifying the current password.

The static analyzer supports the seeded `NPE`, `OOM`, `CS`, `COMMENT`, `LONG_METHOD`, and `DUPLICATE_CODE` rules. A long method is currently defined as over 40 source lines; duplicate code compares normalized method bodies. Teacher Reports exports the scoped grade list as CSV or XLSX. Code-similarity scores compare token shingles within an assignment and are only review signals, not proof of misconduct.

The default storage is local JSON under `storage/`; it does not modify the repository CSV data or connect to SQL Server unless `PORTAL_STORAGE=sqlserver` is configured.

## AI feedback configuration

Set `AI_PROVIDER=openai` with `OPENAI_API_KEY`, or `AI_PROVIDER=gemini` with `GEMINI_API_KEY`, in `.env`. Set `AI_PROVIDER=disabled` to keep analysis local. AI feedback is explanatory only: it does not create findings, change severities, or affect the score. If the selected provider is misconfigured or unavailable, local findings are retained and a warning is shown. Provider integration tests use mocked responses; a live request requires a valid key and network access.

## Save portal changes to SQL Server

The portal can store its runtime state in the same SQL Server database used by the Java project. Start SQL Server, ensure the `AITA_DB` database exists, then run `Copy-Item .env.example .env` from `portal-node` and set:

```env
PORTAL_STORAGE=sqlserver
DB_SERVER=localhost
DB_PORT=1433
DB_NAME=AITA_DB
DB_USER=your_sql_login
DB_PASSWORD=your_sql_password
DB_ENCRYPT=false
DB_TRUST_SERVER_CERTIFICATE=true
```

Run the portal from `portal-node` with `npm.cmd install` and `npm.cmd start`. The portal creates `dbo.PortalRuntimeState` automatically. If the SQL table is empty, the portal imports the existing `storage/portal-data.json` snapshot when available, otherwise it seeds runtime data from the repository CSV files. Once SQL storage is selected, changes are saved in SQL and each modifying API waits for the database write to finish. CSV seed files are not modified.

To inspect the saved state, connect to `AITA_DB` in SQL Server Management Studio (SSMS), open a New Query window, and run:

```sql
USE AITA_DB;

SELECT StateId, UpdatedAt, ISJSON(RuntimeJson) AS IsValidJson
FROM dbo.PortalRuntimeState;

SELECT grade.studentId, grade.courseCode, grade.semester,
			 grade.totalScore, grade.manualFeedback
FROM dbo.PortalRuntimeState AS state
CROSS APPLY OPENJSON(state.RuntimeJson, '$.courseGrades')
WITH (
	studentId nvarchar(30) '$.studentId',
	courseCode nvarchar(30) '$.courseCode',
	semester nvarchar(30) '$.semester',
	totalScore decimal(4,1) '$.totalScore',
	manualFeedback nvarchar(4000) '$.manualFeedback'
) AS grade;

SELECT submission.studentId, submission.fileName, submission.status, submission.submittedAt
FROM dbo.PortalRuntimeState AS state
CROSS APPLY OPENJSON(state.RuntimeJson, '$.submissions')
WITH (
	studentId nvarchar(30) '$.userId',
	fileName nvarchar(255) '$.fileName',
	status nvarchar(30) '$.status',
	submittedAt datetime2 '$.submittedAt'
) AS submission;
```

`PortalRuntimeState` stores one JSON snapshot so the portal can persist all existing workflows without changing their API. CSV files remain the source for seed users and rosters; enrollment overrides, grades, assignments, submissions, feedback, and other portal changes are in the SQL snapshot. To switch back to JSON storage, set `PORTAL_STORAGE=json`.

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
- `PATCH /api/me/profile`
- `PATCH /api/me/password`
- `GET /api/me/notifications`
- `PATCH /api/me/notifications/:notificationId/read`
- `POST /api/student/submissions` (multipart field `sourceFile`)
- `GET /api/student/submissions`
- `GET /api/student/grades`
- `GET /api/student/course-grades`
- `GET /api/student/courses`
- `GET /api/student/assignments`
- `PATCH /api/student/assignments/:assignmentId/read`
- `GET /api/teacher/submissions`
- `GET /api/teacher/courses`
- `GET /api/teacher/classes`
- `PATCH /api/teacher/classes/:courseCode/enrollments/:studentId`
- `PATCH /api/teacher/classes/:courseCode/teachers`
- `GET /api/teacher/course-grades`
- `PATCH /api/teacher/course-grades/:studentId`
- `PATCH /api/teacher/course-grades/:studentId/feedback`
- `GET /api/teacher/assignments`
- `POST /api/teacher/assignments`
- `PATCH /api/teacher/assignments/:assignmentId`
- `DELETE /api/teacher/assignments/:assignmentId`
- `GET /api/teacher/report`
- `GET /api/teacher/report.csv`
- `GET /api/teacher/report.xlsx`
- `GET /api/teacher/assignments/:assignmentId/submissions`
- `GET /api/teacher/assignments/:assignmentId/similar-submissions`
- `POST /api/teacher/submissions/:submissionId/analyze`
- `GET /api/teacher/submissions/:submissionId/audit`
- `GET /api/teacher/rules`
- `PATCH /api/teacher/rules/:ruleCode`
- `PATCH /api/teacher/submissions/:submissionId/grade`
- `PATCH /api/teacher/submissions/:submissionId/feedback`
- `GET /api/health`