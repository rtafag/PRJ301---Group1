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

Open `http://localhost:3000`. User IDs run from `GV0001` for teachers and `HE001` for students. Usernames use each roster email's local part, and seeded demo accounts use `password123`; duplicate email local parts include the lower-case user ID as a suffix. These are sample credentials, not production passwords.

## Password recovery

The login screen can reset a password after matching the account's registered email and User ID. The new password must contain at least 8 characters and is stored as a scrypt hash in the local runtime data. This demo validates the email but does not send email; production recovery should use a short-lived, single-use token delivered to the account email instead of relying on a User ID.

## Implemented workflows

- Student login, semester/course grade overview, persistent deadline/assignment/grade/feedback notifications, profile and password settings, source upload, SHA-256 duplicate detection, Java syntax parsing, static rule checks, and personal grades.
- Teacher course/semester grade overview, editable class enrollments and teacher assignments, per-course grade editing and manual feedback, rule configuration, assignment management, submission review, plagiarism signals, grade audit history, reports, and CSV/XLSX export.
- Optional OpenAI or Gemini feedback, enabled only by setting the provider and its API key in `.env`. When enabled, the submitted source code is sent to that provider for feedback.
- Optional Docker execution sandbox. It is disabled by default; submitted programs are never executed unless explicitly enabled and Docker is available.

## Teacher course overview

The course selector seeds course codes and enrollments from `../data/students.csv` and teacher assignments from `../data/teachers.csv`. Account IDs are mapped in source CSV order and initial students are distributed evenly among assigned teachers. Changes to enrollments and course-teacher assignments are stored separately in `storage/portal-data.json`; they never rewrite the source CSV files. Since the CSV data has no per-course grades or semester field, demo grades are generated from student GPA for Fall 2026; other semester choices are empty. Course names are not present in the supplied data, so courses are displayed by code.

Manual feedback is stored with the selected course, student, and semester. Teachers can create, edit, and delete assignments for their courses, adjust deadlines, and see `Active` or `Expired` status based on the current time.

Students see semester and enrolled course selectors in Overview. The notification inbox includes upcoming deadlines (within 48 hours), new assignments, updated grades, and new feedback, with persistent read state. Account settings allow users to update their name/email and change their password after verifying the current password.

The static analyzer supports the seeded `NPE`, `OOM`, `CS`, `COMMENT`, `LONG_METHOD`, and `DUPLICATE_CODE` rules. A long method is currently defined as over 40 source lines; duplicate code compares normalized method bodies. Teacher Reports exports the scoped grade list as CSV or XLSX. Code-similarity scores compare token shingles within an assignment and are only review signals, not proof of misconduct.

The default storage is local JSON under `storage/`; it does not modify the repository CSV data or connect to SQL Server unless `PORTAL_STORAGE=sqlserver` is configured.

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

## Sandbox

The sandbox is disabled by default. To run it locally on Windows with Docker Desktop:

1. Start Docker Desktop and make sure it uses **Linux containers**. In PowerShell, confirm the daemon is ready:

   ```powershell
   docker info
   ```

2. From the `portal-node` directory, build the local Java runtime image:

   ```powershell
   docker build -t aita-java-sandbox:8 -f sandbox\Dockerfile sandbox
   docker image inspect aita-java-sandbox:8
   ```

   The runner uses `--pull=never`, so it will not download an image during a student request.

3. Start Redis if it is not already running:

   ```powershell
   docker run -d --name aita-redis -p 6379:6379 redis:7-alpine
   ```

   If the `aita-redis` container already exists but is stopped, run `docker start aita-redis` instead.

4. Install Node.js 22 or newer. Copy `.env.example` to `.env` if needed, then set:

   ```env
   REDIS_URL=redis://127.0.0.1:6379
   SANDBOX_ENABLED=true
   SANDBOX_IMAGE=aita-java-sandbox:8
   ```

5. In PowerShell, start the portal:

   ```powershell
   npm.cmd install
   npm.cmd start
   ```

6. Open `http://localhost:3000`, sign in as a demo student, submit a `.java` file with a `main` method, and open **Analysis & fixes**. Select **Run in sandbox**. The output appears below the button. The API endpoint is `POST /api/student/submissions/:submissionId/run`; it only runs a Java source file owned by the authenticated student.

The runner executes each program in a fresh container with no network, a read-only source mount, a non-root user, dropped Linux capabilities, `no-new-privileges`, 128 MiB memory, 0.5 CPU, 32 processes, a 12-second timeout, and bounded output. The named container is explicitly killed if it exceeds the timeout. The submitted source is removed from the server's temporary directory after the run. Do not expose this demo portal directly to the public internet.

If PowerShell says the Docker engine cannot be reached, start Docker Desktop and wait for `docker info` to complete successfully. If the image is missing, rebuild it with the command in step 2. To stop the optional Redis container after the demo:

```powershell
docker rm -f aita-redis
```

Sandbox unit tests use a mocked Docker process and run without a Docker daemon:

```powershell
npm.cmd test
```

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