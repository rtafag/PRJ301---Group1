import 'dotenv/config';
import crypto from 'node:crypto';
import express from 'express';
import ExcelJS from 'exceljs';
import helmet from 'helmet';
import multer from 'multer';
import path from 'node:path';
import { analyzeJavaSource } from './ai-engine.js';
import { AnalysisQueue, getRetryDelay, getSubmissionPriority } from './analysis-queue.js';
import { createAuth, requireAuth } from './auth.js';
import { runJavaInSandbox } from './sandbox.js';
import { createSqlRuntimeRepository } from './sql-store.js';
import { createStore, portalDirectory } from './store.js';

const app = express();
const sqlRepository = process.env.PORTAL_STORAGE?.toLowerCase() === 'sqlserver'
  ? await createSqlRuntimeRepository()
  : null;
const store = createStore(sqlRepository ? {
  runtimeData: await sqlRepository.load(),
  saveRuntimeData: (runtimeData) => sqlRepository.save(runtimeData)
} : {});
if (sqlRepository) await sqlRepository.save(store.getRuntimeData());
const analysisQueue = new AnalysisQueue();
const auth = createAuth(store);
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 512 * 1024, files: 20, fields: 10, parts: 30 }
});
const studentOnly = requireAuth(auth, 'Student');
const teacherOnly = requireAuth(auth, 'Teacher');
let workerRunning = true;

function sandboxEnabled() {
  return (process.env.SANDBOX_ENABLED ?? 'true').toLowerCase() !== 'false';
}

async function processAnalysisJob(job) {
  const submission = store.findSubmission(job.submissionId);
  if (!submission) {
    console.error(`Analysis job ${job.submissionId} has no matching submission; discarding it.`);
    await analysisQueue.acknowledge(job.submissionId);
    return;
  }
  if (submission.analysis) {
    await analysisQueue.acknowledge(job.submissionId);
    return;
  }

  store.updateSubmissionStatus(job.submissionId, 'Processing');
  await store.flushPersistence();
  const heartbeat = setInterval(() => {
    analysisQueue.heartbeat(job.submissionId).catch((error) => {
      console.error(`Unable to renew analysis job ${job.submissionId}:`, error);
    });
  }, 30000);

  try {
    const startedAt = Date.now();
    const analysis = await analyzeJavaSource(submission.sourceCode, store.listRules());
    store.updateAnalysis(job.submissionId, {
      astJson: analysis.astSummary,
      aiFeedback: analysis.aiFeedback,
      providerWarning: analysis.providerWarning,
      syntaxValid: analysis.syntaxValid,
      executionTimeMs: Date.now() - startedAt,
      violations: analysis.violations
    }, {
      totalScore: analysis.totalScore,
      deduction: analysis.deduction,
      gradingNotes: analysis.violations.length ? 'Rule engine findings are available for review.' : 'No configured rule violations found.'
    });
    await store.flushPersistence();
  } catch (error) {
    console.error(`Analysis attempt ${job.attemptsMade}/${job.maxAttempts} failed for ${job.submissionId}:`, error);
    if (job.attemptsMade >= job.maxAttempts) {
      store.updateSubmissionStatus(job.submissionId, 'Analysis Failed');
      await store.flushPersistence();
      await analysisQueue.acknowledge(job.submissionId);
      return;
    }
    store.updateSubmissionStatus(job.submissionId, 'Queued');
    await store.flushPersistence();
    await analysisQueue.retry(job, getRetryDelay(job.attemptsMade));
    return;
  } finally {
    clearInterval(heartbeat);
  }
  await analysisQueue.acknowledge(job.submissionId);
}

async function runAnalysisWorker() {
  let lastReconciliationAt = 0;
  while (workerRunning) {
    try {
      const reserved = await analysisQueue.reserve();
      if (!reserved) {
        if (Date.now() - lastReconciliationAt >= 5000) {
          lastReconciliationAt = Date.now();
          for (const submission of store.listQueueFailedSubmissions()) {
            try {
              await analysisQueue.enqueue({
                submissionId: submission.submissionId,
                priority: getSubmissionPriority(
                  submission.assignmentId
                    ? store.findAssignment(submission.assignmentId)
                    : null
                )
              });
              store.updateSubmissionStatus(submission.submissionId, 'Queued');
              await store.flushPersistence();
            } catch (error) {
              console.error(`Unable to requeue analysis for ${submission.submissionId}:`, error);
              break;
            }
          }
        }
        await new Promise((resolve) => setTimeout(resolve, 1000));
        continue;
      }
      if (reserved.type === 'failed') {
        const submission = store.findSubmission(reserved.job.submissionId);
        if (submission && !submission.analysis) {
          store.updateSubmissionStatus(reserved.job.submissionId, 'Analysis Failed');
          await store.flushPersistence();
        }
        continue;
      }
      await processAnalysisJob(reserved.job);
    } catch (error) {
      console.error('Analysis queue worker could not reach Redis or process a job:', error);
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
  }
}

app.disable('x-powered-by');
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      styleSrc: ["'self'", "'unsafe-inline'", 'https://fonts.googleapis.com'],
      fontSrc: ["'self'", 'https://fonts.gstatic.com', 'data:']
    }
  }
}));
app.use(express.json({ limit: '64kb' }));
app.use(express.static(path.join(portalDirectory, 'public')));

function publicSubmission(submission) {
  const { sourceCode, fileData, sourceFiles, ...visible } = submission;
  return {
    ...visible,
    fileNames: Array.isArray(sourceFiles) && sourceFiles.length
      ? sourceFiles.map((file) => file.fileName)
      : [submission.fileName],
    hasSourceCode: typeof sourceCode === 'string' && sourceCode.length > 0,
    hasJavaSource: (Array.isArray(sourceFiles) && sourceFiles.length > 0)
      || (isJavaFile(submission.fileName) && typeof sourceCode === 'string' && sourceCode.length > 0)
  };
}

function isJavaFile(fileName) {
  return path.extname(fileName).toLowerCase() === '.java';
}

function isTextFile(fileName, mimeType) {
  return mimeType.startsWith('text/') || /\.(?:java|js|ts|py|rb|php|html?|css|xml|json|ya?ml|md|sql|c|h|cc|cpp|hpp|cs|sh|bat|ps1)$/i.test(fileName);
}

function publicAssignment(assignment) {
  const { readBy, ...visible } = assignment;
   return {
     ...visible,
     status: assignment.closed ? 'Closed' : new Date(assignment.dueAt).getTime() <= Date.now() ? 'Expired' : 'Active'
  };
}

function csvCell(value) {
  return `"${String(value ?? '').replaceAll('"', '""')}"`;
}

function getOwnedSubmission(request, response) {
  const submission = store.findSubmission(request.params.submissionId);
  if (!submission || submission.userId !== request.user.userId) {
    response.status(404).json({ error: 'Submission not found.' });
    return null;
  }
  return submission;
}

app.get('/api/health', async (request, response) => {
  let queueStatus = 'ready';
  try {
    await analysisQueue.ping();
  } catch (error) {
    queueStatus = 'unavailable';
    console.error('Redis health check failed:', error);
  }
  response.status(queueStatus === 'ready' ? 200 : 503).json({
    status: queueStatus === 'ready' ? 'ok' : 'degraded',
    analysisQueue: queueStatus,
    aiProvider: process.env.AI_PROVIDER || 'disabled',
    sandboxEnabled: sandboxEnabled(),
    storage: sqlRepository ? 'sqlserver' : 'json'
  });
});

app.post('/api/auth/login', (request, response) => {
  const result = auth.login(request.body ?? {});
  if (!result) return response.status(401).json({ error: 'Invalid username or password.' });
  return response.json(result);
});

app.post('/api/auth/password-reset', async (request, response) => {
  const { email, confirmationKey, newPassword } = request.body ?? {};
  if (typeof email !== 'string' || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
      || typeof confirmationKey !== 'string' || !confirmationKey.trim()
      || typeof newPassword !== 'string' || newPassword.length < 8) {
    return response.status(400).json({ error: 'Enter a valid email, confirmation key, and password of at least 8 characters.' });
  }
  if (!auth.resetPassword({ email, confirmationKey, newPassword })) {
    return response.status(400).json({ error: 'Email and confirmation key do not match an account.' });
  }
  await store.flushPersistence();
  return response.json({ message: 'Password updated. You can now sign in with your new password.' });
});

app.post('/api/auth/logout', requireAuth(auth), (request, response) => {
  auth.logout(request.sessionToken);
  return response.status(204).end();
});

app.get('/api/me', requireAuth(auth), (request, response) => {
  const { passwordHash, ...user } = store.findUserById(request.user.userId);
  response.json({ user });
});

app.patch('/api/me/profile', requireAuth(auth), async (request, response) => {
  const fullName = String(request.body?.fullName ?? '').trim();
  const email = String(request.body?.email ?? '').trim().toLowerCase();
  if (!fullName || fullName.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return response.status(400).json({ error: 'Enter a name under 120 characters and a valid email address.' });
  }
  if (store.users.some((user) => user.userId !== request.user.userId && user.email.toLowerCase() === email)) {
    return response.status(409).json({ error: 'This email address is already used by another account.' });
  }
  const user = store.updateUserProfile(request.user.userId, { fullName, email });
  await store.flushPersistence();
  return response.json({ user });
});

app.patch('/api/me/password', requireAuth(auth), async (request, response) => {
  const { currentPassword, newPassword } = request.body ?? {};
  if (typeof newPassword !== 'string' || newPassword.length < 8 || newPassword.length > 128) {
    return response.status(400).json({ error: 'New password must contain 8 to 128 characters.' });
  }
  if (!auth.changePassword({ userId: request.user.userId, currentPassword, newPassword })) {
    return response.status(400).json({ error: 'Current password is incorrect.' });
  }
  await store.flushPersistence();
  return response.json({ message: 'Password updated.' });
});

app.get('/api/me/notifications', requireAuth(auth), (request, response) => {
  return response.json({ notifications: store.listNotifications(request.user.userId) });
});

app.patch('/api/me/notifications/:notificationId/read', requireAuth(auth), async (request, response) => {
  if (!store.markNotificationRead(request.user.userId, request.params.notificationId)) {
    return response.status(404).json({ error: 'Notification not found.' });
  }
  await store.flushPersistence();
  return response.status(204).end();
});

app.post('/api/student/submissions', studentOnly, (request, response, next) => {
  upload.array('sourceFile', 20)(request, response, async (error) => {
    if (error) return next(error);
    const uploadedFiles = request.files ?? [];
    if (!uploadedFiles.length) return response.status(400).json({ error: 'Choose at least one file to submit.' });

    const files = uploadedFiles.map((file) => ({
      ...file,
      fileName: path.basename(String(file.originalname).replaceAll('\\', '/'))
    }));
    if (files.some((file) => !file.fileName || file.fileName.length > 255 || /[\u0000-\u001f]/.test(file.fileName))) {
      return response.status(400).json({ error: 'A file name is invalid or exceeds 255 characters.' });
    }
    if (files.length > 1 && files.some((file) => !isJavaFile(file.fileName))) {
      return response.status(415).json({ error: 'Multiple-file submissions must contain only .java files.' });
    }
    const totalBytes = files.reduce((total, file) => total + file.size, 0);
    if (totalBytes > 512 * 1024) {
      return response.status(413).json({ error: 'Combined submission size must be 512 KB or smaller.' });
    }

    const names = files.map((file) => file.fileName.toLowerCase());
    if (new Set(names).size !== names.length) {
      return response.status(400).json({ error: 'Submitted files must have unique filenames.' });
    }

    const mainFileName = String(request.body?.mainFile ?? files[0].fileName);
    const mainFile = files.find((file) => file.fileName === mainFileName);
    if (!mainFile) {
      return response.status(400).json({ error: 'Choose an entry-point file from the selected Java files.' });
    }

    if (files.length > 1 && files.some((file) => !/^[A-Za-z_$][A-Za-z0-9_$]*\.java$/i.test(file.fileName))) {
      return response.status(400).json({ error: 'Java filenames must be valid class names ending in .java.' });
    }

    const fileName = mainFile.fileName;
    const javaFile = isJavaFile(fileName);
    const mimeType = mainFile.mimetype || 'application/octet-stream';
    const sourceCode = isTextFile(fileName, mimeType) ? mainFile.buffer.toString('utf8') : null;
    const sourceFiles = files.every((file) => isJavaFile(file.fileName))
      ? files.map((file) => ({ fileName: file.fileName, sourceCode: file.buffer.toString('utf8') }))
      : null;
    const assignmentId = String(request.body?.assignmentId ?? '').trim();
    const assignment = assignmentId
      ? store.listAssignmentsForStudent(request.user.userId).find((item) => item.assignmentId === assignmentId)
      : null;
    if (assignmentId && !assignment) return response.status(404).json({ error: 'Assignment not found for your courses.' });
    if (assignment?.closed) return response.status(409).json({ error: 'This assignment is closed and no longer accepts submissions.' });
    const hash = crypto.createHash('sha256');
    if (files.length === 1) {
      hash.update(mainFile.buffer);
    } else {
      for (const file of [...files].sort((first, second) => first.fileName.localeCompare(second.fileName))) {
        hash.update(`${Buffer.byteLength(file.fileName)}:${file.fileName}:${file.size}:`);
        hash.update(file.buffer);
      }
    }
    const fileHash = hash.digest('hex');
    const duplicate = store.findSubmissionByHash(fileHash);
    if (duplicate) {
      const message = duplicate.userId === request.user.userId
        ? 'You have already submitted this source file.'
        : 'This source matches an existing submission.';
      return response.status(409).json({ error: message });
    }

    const submissionId = `SUB-${crypto.randomUUID()}`;
    const submission = store.createSubmission({
      submissionId,
      userId: request.user.userId,
      assignmentId: assignment?.assignmentId ?? null,
      fileName,
      mainFileName: fileName,
      fileNames: files.map((file) => file.fileName),
      mimeType,
      fileData: mainFile.buffer.toString('base64'),
      fileUrl: `local://${request.user.userId}/${fileName}`,
      fileHash,
      sourceCode,
      sourceFiles,
      status: javaFile ? 'Queued' : 'Submitted',
      score: null,
      analysis: null,
      manualFeedback: ''
    });

    try {
      await store.flushPersistence();
      if (javaFile) {
        await analysisQueue.enqueue({
          submissionId,
          priority: getSubmissionPriority(assignment)
        });
      }
    } catch (error) {
      if (javaFile) {
        store.updateSubmissionStatus(submissionId, 'Queue Failed');
        try {
          await store.flushPersistence();
        } catch (persistenceError) {
          return next(persistenceError);
        }
        console.error(`Unable to enqueue analysis for ${submissionId}:`, error);
        return response.status(503).json({
          error: 'The submission was saved, but analysis could not be queued. The server will retry automatically.',
          submission: publicSubmission(submission)
        });
      }
      return next(error);
    }
    return response.status(201).json({ submission: publicSubmission(submission) });
  });
});

app.get('/api/student/submissions/:submissionId/file', studentOnly, (request, response) => {
  const submission = getOwnedSubmission(request, response);
  if (!submission) return;
  if (!submission.fileData) return response.status(404).json({ error: 'Original file is not available.' });
  const bytes = Buffer.from(submission.fileData, 'base64');
  response.setHeader('Content-Type', submission.mimeType || 'application/octet-stream');
  response.setHeader('Content-Length', bytes.length);
  response.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(submission.fileName)}`);
  response.setHeader('X-Content-Type-Options', 'nosniff');
  return response.send(bytes);
});

app.get('/api/student/submissions', studentOnly, (request, response) => {
  response.json({ submissions: store.listSubmissionsForUser(request.user.userId).map(publicSubmission) });
});

app.get('/api/student/grades', studentOnly, (request, response) => {
  const grades = store.listSubmissionsForUser(request.user.userId)
    .filter((submission) => submission.score)
    .map((submission) => ({
      submissionId: submission.submissionId,
      fileName: submission.fileName,
      totalScore: submission.score.totalScore,
      deduction: submission.score.deduction,
      gradingNotes: submission.score.gradingNotes,
      updatedAt: submission.score.updatedAt,
      manuallyEdited: submission.score.manuallyEdited
    }));
  response.json({ grades });
});

app.get('/api/student/course-grades', studentOnly, (request, response) => {
  response.json({ courseGrades: store.listCourseGradesForStudent(request.user.userId) });
});

app.get('/api/student/courses', studentOnly, (request, response) => {
  response.json({
    courses: store.listCoursesForStudent(request.user.userId),
    semesters: store.listSemesters()
  });
});

app.get('/api/student/assignments', studentOnly, (request, response) => {
  const assignments = store.listAssignmentsForStudent(request.user.userId)
    .map((assignment) => ({ ...publicAssignment(assignment), isRead: assignment.isRead }));
  return response.json({ assignments });
});

app.patch('/api/student/assignments/:assignmentId/read', studentOnly, async (request, response) => {
  const assignment = store.markAssignmentRead(request.user.userId, request.params.assignmentId);
  if (!assignment) return response.status(404).json({ error: 'Assignment not found for your courses.' });
  await store.flushPersistence();
  return response.json({ assignment: { ...publicAssignment(assignment), isRead: true } });
});

app.get('/api/student/submissions/:submissionId', studentOnly, (request, response) => {
  const submission = getOwnedSubmission(request, response);
  if (submission) response.json({ submission: publicSubmission(submission) });
});

app.post('/api/student/submissions/:submissionId/run', studentOnly, async (request, response, next) => {
  const submission = getOwnedSubmission(request, response);
  if (!submission) return;
  if (!isJavaFile(submission.fileName)) {
    return response.status(415).json({ error: 'The Java sandbox only accepts .java submissions.' });
  }
  const sourceFiles = Array.isArray(submission.sourceFiles) && submission.sourceFiles.length
    ? submission.sourceFiles
    : submission.sourceCode
      ? [{ fileName: submission.fileName, sourceCode: submission.sourceCode }]
      : [];
  if (!sourceFiles.length) {
    return response.status(409).json({ error: 'Source code is not available for this sample submission.' });
  }
  try {
    const result = await runJavaInSandbox(sourceFiles, submission.mainFileName ?? submission.fileName);
    return response.json({ sandbox: result });
  } catch (error) {
    return next(error);
  }
});

app.get('/api/teacher/submissions', teacherOnly, (request, response) => {
  const students = new Map(store.users.map((user) => [user.userId, user.fullName]));
  const submissions = store.listSubmissionsForTeacher(request.user.userId).map((submission) => ({
    ...publicSubmission(submission),
    studentName: students.get(submission.userId) ?? 'Unknown student'
  }));
  response.json({ submissions });
});

app.get('/api/teacher/courses', teacherOnly, (request, response) => {
  response.json({
    courses: store.listCoursesForTeacher(request.user.userId),
    semesters: store.listSemesters()
  });
});

app.get('/api/teacher/classes', teacherOnly, (request, response) => {
  return response.json({
    classes: store.listClassManagement(request.user.userId),
    teachers: store.listTeachers(),
    students: store.listStudents()
  });
});

app.patch('/api/teacher/classes/:courseCode/enrollments/:studentId', teacherOnly, async (request, response) => {
  if (typeof request.body?.enrolled !== 'boolean') return response.status(400).json({ error: 'Enrolled must be a boolean.' });
  const result = store.updateEnrollment(request.user.userId, request.params.courseCode, request.params.studentId, request.body.enrolled);
  if (result === null) return response.status(403).json({ error: 'This course is not assigned to your account.' });
  if (!result) return response.status(404).json({ error: 'Student not found.' });
  await store.flushPersistence();
  return response.status(204).end();
});

app.patch('/api/teacher/classes/:courseCode/teachers', teacherOnly, async (request, response) => {
  const result = store.updateCourseTeachers(request.user.userId, request.params.courseCode, request.body?.teacherIds);
  if (result === null) return response.status(403).json({ error: 'This course is not assigned to your account.' });
  if (!result) return response.status(400).json({ error: 'Assign at least one valid teacher.' });
  await store.flushPersistence();
  return response.json({ courseCode: request.params.courseCode, teacherIds: request.body.teacherIds });
});

app.get('/api/teacher/course-grades', teacherOnly, (request, response) => {
  const courseCode = String(request.query.courseCode ?? '').trim();
  const semester = String(request.query.semester ?? '').trim();
  if (!courseCode || !semester) {
    return response.status(400).json({ error: 'Select a course and semester.' });
  }
  const grades = store.listCourseGrades(request.user.userId, courseCode, semester);
  if (grades === null) return response.status(403).json({ error: 'This course is not assigned to your account.' });
  return response.json({ grades });
});

app.patch('/api/teacher/course-grades/:studentId', teacherOnly, async (request, response) => {
  const courseCode = String(request.body?.courseCode ?? '').trim();
  const semester = String(request.body?.semester ?? '').trim();
  const totalScore = Number(request.body?.totalScore);
  const deduction = Number(request.body?.deduction ?? 0);
  if (!Number.isFinite(totalScore) || totalScore < 0 || totalScore > 10
      || !Number.isFinite(deduction) || deduction < 0 || deduction > 10) {
    return response.status(400).json({ error: 'Score and deduction must be between 0 and 10.' });
  }
  if (!store.listCoursesForTeacher(request.user.userId).some((course) => course.courseCode === courseCode)) {
    return response.status(403).json({ error: 'This course is not assigned to your account.' });
  }
  const grade = store.updateCourseGrade(request.user.userId, courseCode, semester, request.params.studentId, {
    totalScore,
    deduction,
    gradingNotes: String(request.body?.gradingNotes ?? '').slice(0, 2000)
  });
  if (!grade) return response.status(404).json({ error: 'Course grade not found.' });
  await store.flushPersistence();
  return response.json({ grade });
});

app.patch('/api/teacher/course-grades/:studentId/feedback', teacherOnly, async (request, response) => {
  const courseCode = String(request.body?.courseCode ?? '').trim();
  const semester = String(request.body?.semester ?? '').trim();
  const manualFeedback = String(request.body?.manualFeedback ?? '').trim();
  if (manualFeedback.length > 4000) {
    return response.status(400).json({ error: 'Feedback must be 4000 characters or fewer.' });
  }
  if (!store.listCoursesForTeacher(request.user.userId).some((course) => course.courseCode === courseCode)) {
    return response.status(403).json({ error: 'This course is not assigned to your account.' });
  }
  const grade = store.updateCourseFeedback(request.user.userId, courseCode, semester, request.params.studentId, manualFeedback);
  if (!grade) return response.status(404).json({ error: 'Course grade not found.' });
  await store.flushPersistence();
  return response.json({ grade });
});

app.get('/api/teacher/assignments', teacherOnly, (request, response) => {
  const assignments = store.listAssignmentsForTeacher(request.user.userId).map(publicAssignment);
  return response.json({ assignments });
});

app.get('/api/teacher/assignments/:assignmentId/submissions', teacherOnly, (request, response) => {
  const assignment = store.findAssignmentForTeacher(request.user.userId, request.params.assignmentId);
  if (!assignment) return response.status(404).json({ error: 'Assignment not found.' });
  const students = store.listStudentsForAssignment(request.user.userId, assignment.assignmentId).map((student) => {
    const submission = student.submission;
    return {
      studentId: student.studentId,
      fullName: student.fullName,
      submitted: Boolean(submission),
      submittedAt: submission?.submittedAt ?? null,
      late: Boolean(submission && new Date(submission.submittedAt).getTime() > new Date(assignment.dueAt).getTime()),
      submission: submission ? {
        submissionId: submission.submissionId,
        fileName: submission.fileName,
        status: submission.status,
        score: submission.score,
        analysis: submission.analysis
      } : null
    };
  });
  return response.json({ assignment: publicAssignment(assignment), students });
});

app.get('/api/teacher/assignments/:assignmentId/similar-submissions', teacherOnly, (request, response) => {
  const pairs = store.findSimilarSubmissions(request.user.userId, request.params.assignmentId);
  if (!pairs) return response.status(404).json({ error: 'Assignment not found.' });
  return response.json({ pairs });
});

app.get('/api/teacher/submissions/:submissionId', teacherOnly, (request, response) => {
  const submission = store.findSubmissionForTeacher(request.user.userId, request.params.submissionId);
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  const student = store.users.find((user) => user.userId === submission.userId);
  const { fileData, ...visibleSubmission } = submission;
  return response.json({ submission: { ...visibleSubmission, studentName: student?.fullName ?? 'Unknown student' } });
});

app.get('/api/teacher/submissions/:submissionId/file', teacherOnly, (request, response) => {
  const submission = store.findSubmissionForTeacher(request.user.userId, request.params.submissionId);
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  if (!submission.fileData) return response.status(404).json({ error: 'Original file is not available.' });
  const bytes = Buffer.from(submission.fileData, 'base64');
  response.setHeader('Content-Type', submission.mimeType || 'application/octet-stream');
  response.setHeader('Content-Length', bytes.length);
  response.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(submission.fileName)}`);
  response.setHeader('X-Content-Type-Options', 'nosniff');
  return response.send(bytes);
});

app.get('/api/teacher/submissions/:submissionId/audit', teacherOnly, (request, response) => {
  const entries = store.listGradeAuditForSubmission(request.user.userId, request.params.submissionId);
  if (!entries) return response.status(404).json({ error: 'Submission not found.' });
  const names = new Map(store.users.map((user) => [user.userId, user.fullName]));
  return response.json({ entries: entries.map((entry) => ({ ...entry, actorName: names.get(entry.actorUserId) ?? entry.actorUserId })) });
});

app.post('/api/teacher/submissions/:submissionId/analyze', teacherOnly, async (request, response, next) => {
  const submission = store.findSubmissionForTeacher(request.user.userId, request.params.submissionId);
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  if (!isJavaFile(submission.fileName)) {
    return response.status(415).json({ error: 'The rule engine only analyzes .java submissions.' });
  }
  if (!submission.sourceCode) return response.status(409).json({ error: 'Source code is not available for this submission.' });
  try {
    const startedAt = Date.now();
    const analysis = await analyzeJavaSource(submission.sourceCode, store.listRules());
    const updated = store.updateAnalysis(submission.submissionId, {
      astJson: analysis.astSummary,
      aiFeedback: analysis.aiFeedback,
      providerWarning: analysis.providerWarning,
      syntaxValid: analysis.syntaxValid,
      executionTimeMs: Date.now() - startedAt,
      violations: analysis.violations
    }, {
      totalScore: analysis.totalScore,
      deduction: analysis.deduction,
      gradingNotes: analysis.violations.length ? 'Rule engine findings are available for review.' : 'No configured rule violations found.'
    });
    await store.flushPersistence();
    return response.json({ submission: updated });
  } catch (error) {
    return next(error);
  }
});

app.post('/api/teacher/assignments', teacherOnly, async (request, response) => {
  const title = String(request.body?.title ?? '').trim();
  const courseCode = String(request.body?.courseCode ?? '').trim();
  const description = String(request.body?.description ?? '').trim();
  const dueAt = new Date(request.body?.dueAt);
  if (!title || title.length > 120 || description.length > 4000 || Number.isNaN(dueAt.valueOf())) {
    return response.status(400).json({ error: 'Enter a title, valid deadline, and description under 4000 characters.' });
  }
  if (!store.listCoursesForTeacher(request.user.userId).some((course) => course.courseCode === courseCode)) {
    return response.status(403).json({ error: 'This course is not assigned to your account.' });
  }
  const assignment = store.createAssignment(request.user.userId, {
    title,
    courseCode,
    description,
    dueAt: dueAt.toISOString()
  });
  await store.flushPersistence();
  return response.status(201).json({ assignment: publicAssignment(assignment) });
});

app.patch('/api/teacher/assignments/:assignmentId', teacherOnly, async (request, response) => {
  const existing = store.findAssignmentForTeacher(request.user.userId, request.params.assignmentId);
  if (!existing) return response.status(404).json({ error: 'Assignment not found.' });
  const title = String(request.body?.title ?? existing.title).trim();
  const courseCode = String(request.body?.courseCode ?? existing.courseCode).trim();
  const description = String(request.body?.description ?? existing.description).trim();
  const dueAt = new Date(request.body?.dueAt ?? existing.dueAt);
  if (!title || title.length > 120 || description.length > 4000 || Number.isNaN(dueAt.valueOf())) {
    return response.status(400).json({ error: 'Enter a title, valid deadline, and description under 4000 characters.' });
  }
  if (!store.listCoursesForTeacher(request.user.userId).some((course) => course.courseCode === courseCode)) {
    return response.status(403).json({ error: 'This course is not assigned to your account.' });
  }
  if (request.body?.closed !== undefined && typeof request.body.closed !== 'boolean') {
    return response.status(400).json({ error: 'Closed must be a boolean.' });
  }
  const assignment = store.updateAssignment(request.user.userId, request.params.assignmentId, {
    title,
    courseCode,
    description,
    dueAt: dueAt.toISOString(),
    closed: request.body?.closed ?? existing.closed ?? false
  });
  await store.flushPersistence();
  return response.json({ assignment: publicAssignment(assignment) });
});

app.delete('/api/teacher/assignments/:assignmentId', teacherOnly, async (request, response) => {
  if (!store.deleteAssignment(request.user.userId, request.params.assignmentId)) {
    return response.status(404).json({ error: 'Assignment not found.' });
  }
  await store.flushPersistence();
  return response.status(204).end();
});

app.get('/api/teacher/report', teacherOnly, (request, response) => {
   response.json({ report: store.createClassReport(request.user.userId) });
});

app.get('/api/teacher/report.csv', teacherOnly, (request, response) => {
  const students = new Map(store.users.map((user) => [user.userId, user.fullName]));
  const rows = store.listSubmissionsForTeacher(request.user.userId).map((submission) => [
    students.get(submission.userId), submission.userId, submission.assignmentId, submission.fileName,
    submission.submittedAt, submission.status, submission.score?.totalScore, submission.score?.deduction,
    submission.score?.gradingNotes, submission.manualFeedback
  ]);
  const csv = [['Student', 'Student ID', 'Assignment ID', 'File', 'Submitted At', 'Status', 'Score', 'Deduction', 'Grading Notes', 'Feedback'], ...rows]
    .map((row) => row.map(csvCell).join(',')).join('\r\n');
  response.setHeader('Content-Type', 'text/csv; charset=utf-8');
  response.setHeader('Content-Disposition', 'attachment; filename="teacher-grades.csv"');
  return response.send(`\uFEFF${csv}`);
});

app.get('/api/teacher/report.xlsx', teacherOnly, async (request, response, next) => {
  try {
    const students = new Map(store.users.map((user) => [user.userId, user.fullName]));
    const workbook = new ExcelJS.Workbook();
    workbook.creator = request.user.fullName;
    workbook.created = new Date();
    const sheet = workbook.addWorksheet('Grades', { views: [{ state: 'frozen', ySplit: 1 }] });
    sheet.columns = [
      { header: 'Student', key: 'student', width: 28 },
      { header: 'Student ID', key: 'studentId', width: 16 },
      { header: 'Assignment ID', key: 'assignmentId', width: 38 },
      { header: 'File', key: 'fileName', width: 32 },
      { header: 'Submitted At', key: 'submittedAt', width: 22, style: { numFmt: 'yyyy-mm-dd hh:mm' } },
      { header: 'Status', key: 'status', width: 16 },
      { header: 'Score', key: 'score', width: 12, style: { numFmt: '0.0' } },
      { header: 'Deduction', key: 'deduction', width: 12, style: { numFmt: '0.0' } },
      { header: 'Grading Notes', key: 'gradingNotes', width: 40 },
      { header: 'Feedback', key: 'feedback', width: 40 }
    ];
    sheet.getRow(1).font = { bold: true, color: { argb: 'FFFFFFFF' } };
    sheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FF174D3B' } };
    for (const submission of store.listSubmissionsForTeacher(request.user.userId)) {
      sheet.addRow({
        student: students.get(submission.userId) ?? 'Unknown student',
        studentId: submission.userId,
        assignmentId: submission.assignmentId ?? '',
        fileName: submission.fileName,
        submittedAt: submission.submittedAt ? new Date(submission.submittedAt) : null,
        status: submission.status,
        score: submission.score?.totalScore ?? null,
        deduction: submission.score?.deduction ?? null,
        gradingNotes: submission.score?.gradingNotes ?? '',
        feedback: submission.manualFeedback ?? ''
      });
    }
    sheet.autoFilter = { from: 'A1', to: 'J1' };
    response.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    response.setHeader('Content-Disposition', 'attachment; filename="teacher-grades.xlsx"');
    await workbook.xlsx.write(response);
    return response.end();
  } catch (error) {
    return next(error);
  }
});

app.get('/api/teacher/rules', teacherOnly, (request, response) => {
  response.json({ rules: store.listRules() });
});

app.patch('/api/teacher/rules/:ruleCode', teacherOnly, async (request, response) => {
  const changes = {};
  if (request.body?.penaltyPoints !== undefined) {
    const penaltyPoints = Number(request.body.penaltyPoints);
    if (!Number.isFinite(penaltyPoints) || penaltyPoints < 0 || penaltyPoints > 10) {
      return response.status(400).json({ error: 'Penalty points must be between 0 and 10.' });
    }
    changes.penaltyPoints = penaltyPoints;
  }
  if (request.body?.enabled !== undefined) {
    if (typeof request.body.enabled !== 'boolean') {
      return response.status(400).json({ error: 'Enabled must be a boolean.' });
    }
    changes.enabled = request.body.enabled;
  }
  const rule = store.updateRule(request.params.ruleCode, changes);
  if (!rule) return response.status(404).json({ error: 'Rule not found.' });
  await store.flushPersistence();
  return response.json({ rule });
});

app.patch('/api/teacher/submissions/:submissionId/grade', teacherOnly, async (request, response) => {
  const totalScore = Number(request.body?.totalScore);
  const deduction = Number(request.body?.deduction ?? 0);
  const reason = String(request.body?.reason ?? '').trim();
  if (!Number.isFinite(totalScore) || totalScore < 0 || totalScore > 10
      || !Number.isFinite(deduction) || deduction < 0 || deduction > 10) {
    return response.status(400).json({ error: 'Score and deduction must be between 0 and 10.' });
  }
  if (!reason || reason.length > 500) return response.status(400).json({ error: 'Enter a grade-change reason under 500 characters.' });
   if (!store.findSubmissionForTeacher(request.user.userId, request.params.submissionId)) {
     return response.status(404).json({ error: 'Submission not found.' });
   }
  const submission = store.updateGrade(request.user.userId, request.params.submissionId, {
    totalScore,
    deduction,
    gradingNotes: String(request.body?.gradingNotes ?? '').slice(0, 2000)
  }, reason);
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  await store.flushPersistence();
  return response.json({ submission: publicSubmission(submission) });
});

app.patch('/api/teacher/submissions/:submissionId/feedback', teacherOnly, async (request, response) => {
  const feedback = String(request.body?.feedback ?? '').trim();
  if (!feedback || feedback.length > 4000) {
    return response.status(400).json({ error: 'Feedback must contain 1 to 4000 characters.' });
  }
   if (!store.findSubmissionForTeacher(request.user.userId, request.params.submissionId)) {
     return response.status(404).json({ error: 'Submission not found.' });
   }
   const submission = store.updateFeedback(request.params.submissionId, feedback);
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  await store.flushPersistence();
  return response.json({ submission: publicSubmission(submission) });
});

app.use((error, request, response, next) => {
  if (response.headersSent) return next(error);
  if (error instanceof multer.MulterError) {
    const message = error.code === 'LIMIT_FILE_SIZE'
      ? 'Each file must be 512 KB or smaller.'
      : error.code === 'LIMIT_FILE_COUNT'
        ? 'Submit no more than 20 files at once.'
        : 'The submission contains too many form fields or files.';
    return response.status(400).json({ error: message });
  }
  console.error(error);
  return response.status(500).json({ error: 'An unexpected server error occurred.' });
});

const port = Number(process.env.PORT) || 3000;
const server = app.listen(port, () => {
  console.log(`Submission Portal - AITA listening at http://localhost:${port}`);
  console.log(`Loaded ${store.users.length} demo users and ${store.listRules().length} rules.`);
  void runAnalysisWorker();
});

async function stopServer() {
  workerRunning = false;
  await new Promise((resolve) => server.close(resolve));
  await sqlRepository?.close();
}

process.once('SIGINT', () => { void stopServer(); });
process.once('SIGTERM', () => { void stopServer(); });
