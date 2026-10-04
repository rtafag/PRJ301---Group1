import 'dotenv/config';
import crypto from 'node:crypto';
import express from 'express';
import helmet from 'helmet';
import multer from 'multer';
import path from 'node:path';
import { analyzeJavaSource, sha256 } from './ai-engine.js';
import { createAuth, requireAuth } from './auth.js';
import { runJavaInSandbox } from './sandbox.js';
import { createStore, portalDirectory } from './store.js';

const app = express();
const store = createStore();
const auth = createAuth(store);
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 512 * 1024, files: 1 }
});
const studentOnly = requireAuth(auth, 'Student');
const teacherOnly = requireAuth(auth, 'Teacher');

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
  const { sourceCode, ...visible } = submission;
  return visible;
}

function getOwnedSubmission(request, response) {
  const submission = store.findSubmission(request.params.submissionId);
  if (!submission || submission.userId !== request.user.userId) {
    response.status(404).json({ error: 'Submission not found.' });
    return null;
  }
  return submission;
}

app.get('/api/health', (request, response) => {
  response.json({
    status: 'ok',
    aiProvider: process.env.AI_PROVIDER || 'disabled',
    sandboxEnabled: process.env.SANDBOX_ENABLED === 'true'
  });
});

app.post('/api/auth/login', (request, response) => {
  const result = auth.login(request.body ?? {});
  if (!result) return response.status(401).json({ error: 'Invalid role or credentials.' });
  return response.json(result);
});

app.post('/api/auth/logout', requireAuth(auth), (request, response) => {
  auth.logout(request.sessionToken);
  return response.status(204).end();
});

app.get('/api/me', requireAuth(auth), (request, response) => {
  response.json({ user: request.user });
});

app.post('/api/student/submissions', studentOnly, (request, response, next) => {
  upload.single('sourceFile')(request, response, (error) => {
    if (error) return next(error);
    if (!request.file) return response.status(400).json({ error: 'Choose a Java source file.' });

    const fileName = path.basename(request.file.originalname);
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*\.java$/.test(fileName)) {
      return response.status(400).json({ error: 'Upload one .java file with a valid Java filename.' });
    }

    const sourceCode = request.file.buffer.toString('utf8');
    const fileHash = sha256(sourceCode);
    const duplicate = store.findSubmissionByHash(fileHash);
    if (duplicate) {
      const message = duplicate.userId === request.user.userId
        ? 'You have already submitted this source file.'
        : 'This source matches an existing submission.';
      return response.status(409).json({ error: message });
    }

    const startedAt = Date.now();
    analyzeJavaSource(sourceCode, store.listRules()).then((analysis) => {
      const submission = store.createSubmission({
        submissionId: `SUB-${crypto.randomUUID()}`,
        userId: request.user.userId,
        fileName,
        fileUrl: `local://${request.user.userId}/${fileName}`,
        fileHash,
        sourceCode,
        status: analysis.syntaxValid ? 'Graded' : 'Needs Review',
        score: {
          totalScore: analysis.totalScore,
          deduction: analysis.deduction,
          gradingNotes: analysis.violations.length ? 'Rule engine findings are available for review.' : 'No configured rule violations found.',
          updatedAt: new Date().toISOString(),
          manuallyEdited: false
        },
        analysis: {
          astJson: analysis.astSummary,
          aiFeedback: analysis.aiFeedback,
          providerWarning: analysis.providerWarning,
          syntaxValid: analysis.syntaxValid,
          executionTimeMs: Date.now() - startedAt,
          violations: analysis.violations
        },
        manualFeedback: ''
      });

      return response.status(201).json({ submission: publicSubmission(submission) });
    }).catch(next);
  });
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

app.get('/api/student/submissions/:submissionId', studentOnly, (request, response) => {
  const submission = getOwnedSubmission(request, response);
  if (submission) response.json({ submission: publicSubmission(submission) });
});

app.post('/api/student/submissions/:submissionId/run', studentOnly, async (request, response, next) => {
  const submission = getOwnedSubmission(request, response);
  if (!submission) return;
  if (!submission.sourceCode) {
    return response.status(409).json({ error: 'Source code is not available for this sample submission.' });
  }
  try {
    const result = await runJavaInSandbox(submission.sourceCode, submission.fileName);
    return response.json({ sandbox: result });
  } catch (error) {
    return next(error);
  }
});

app.get('/api/teacher/submissions', teacherOnly, (request, response) => {
  const students = new Map(store.users.map((user) => [user.userId, user.fullName]));
  const submissions = store.listSubmissions().map((submission) => ({
    ...publicSubmission(submission),
    studentName: students.get(submission.userId) ?? 'Unknown student'
  }));
  response.json({ submissions });
});

app.get('/api/teacher/report', teacherOnly, (request, response) => {
  response.json({ report: store.createClassReport() });
});

app.get('/api/teacher/rules', teacherOnly, (request, response) => {
  response.json({ rules: store.listRules() });
});

app.patch('/api/teacher/rules/:ruleCode', teacherOnly, (request, response) => {
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
  return response.json({ rule });
});

app.patch('/api/teacher/submissions/:submissionId/grade', teacherOnly, (request, response) => {
  const totalScore = Number(request.body?.totalScore);
  const deduction = Number(request.body?.deduction ?? 0);
  if (!Number.isFinite(totalScore) || totalScore < 0 || totalScore > 10
      || !Number.isFinite(deduction) || deduction < 0 || deduction > 10) {
    return response.status(400).json({ error: 'Score and deduction must be between 0 and 10.' });
  }
  const submission = store.updateGrade(request.params.submissionId, {
    totalScore,
    deduction,
    gradingNotes: String(request.body?.gradingNotes ?? '').slice(0, 2000)
  });
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  return response.json({ submission: publicSubmission(submission) });
});

app.patch('/api/teacher/submissions/:submissionId/feedback', teacherOnly, (request, response) => {
  const feedback = String(request.body?.feedback ?? '').trim();
  if (!feedback || feedback.length > 4000) {
    return response.status(400).json({ error: 'Feedback must contain 1 to 4000 characters.' });
  }
  const submission = store.updateFeedback(request.params.submissionId, feedback);
  if (!submission) return response.status(404).json({ error: 'Submission not found.' });
  return response.json({ submission: publicSubmission(submission) });
});

app.use((error, request, response, next) => {
  if (response.headersSent) return next(error);
  if (error instanceof multer.MulterError) {
    return response.status(400).json({ error: error.code === 'LIMIT_FILE_SIZE' ? 'File must be 512 KB or smaller.' : 'Upload one file at a time.' });
  }
  console.error(error);
  return response.status(500).json({ error: 'An unexpected server error occurred.' });
});

const port = Number(process.env.PORT) || 3000;
app.listen(port, () => {
  console.log(`Submission Portal - AITA listening at http://localhost:${port}`);
  console.log(`Loaded ${store.users.length} demo users and ${store.listRules().length} rules.`);
});