import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'csv-parse/sync';
import { randomUUID } from 'node:crypto';

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
export const portalDirectory = path.resolve(moduleDirectory, '..');
export const repositoryDirectory = path.resolve(portalDirectory, '..');
const runtimeDirectory = path.join(portalDirectory, 'storage');
const runtimeFile = path.join(runtimeDirectory, 'portal-data.json');

function readCsv(fileName) {
  const filePath = path.join(repositoryDirectory, 'data', fileName);
  try {
    return parse(fs.readFileSync(filePath, 'utf8'), {
      bom: true,
      columns: true,
      skip_empty_lines: true,
      trim: true
    });
  } catch {
    return [];
  }
}

function readRuntimeData() {
  try {
    return JSON.parse(fs.readFileSync(runtimeFile, 'utf8'));
  } catch {
    return null;
  }
}

function createSeedSubmissions() {
  const scores = new Map(readCsv('score.csv').map((row) => [row.submissionId, row]));
  const analyses = new Map(readCsv('analysis_result.csv').map((row) => [row.submissionId, row]));
  const violationsByAnalysis = new Map();

  for (const row of readCsv('rule_violation.csv')) {
    const violations = violationsByAnalysis.get(row.analysisId) ?? [];
    violations.push({
      ruleId: Number(row.ruleId),
      fileName: row.fileName,
      lineNumber: Number(row.lineNumber),
      suggestedFix: row.suggestedFix
    });
    violationsByAnalysis.set(row.analysisId, violations);
  }

  return readCsv('submission.csv').map((row) => {
    const score = scores.get(row.submissionId);
    const analysis = analyses.get(row.submissionId);
    return {
      submissionId: row.submissionId,
      userId: row.userId,
      fileName: path.basename(row.fileUrl || `${row.submissionId}.java`),
      fileUrl: row.fileUrl,
      fileHash: row.fileHash,
      status: row.status,
      submittedAt: row.submittedAt,
      sourceCode: null,
      score: score ? {
        totalScore: Number(score.totalScore),
        deduction: Number(score.deduction),
        gradingNotes: score.gradingNotes,
        updatedAt: score.updatedAt,
        manuallyEdited: false
      } : null,
      analysis: analysis ? {
        astJson: analysis.astJson,
        aiFeedback: analysis.aiFeedback,
        executionTimeMs: Number(analysis.executionTimeMs),
        violations: violationsByAnalysis.get(analysis.analysisId) ?? []
      } : null,
      manualFeedback: ''
    };
  });
}

function safeNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function createStore() {
  const users = readCsv('users.csv').map((row) => ({
    userId: row.userId,
    username: row.username,
    passwordHash: row.passwordHash,
    fullName: row.fullName,
    email: row.email,
    role: row.role
  }));
  const csvRules = readCsv('rule_definition.csv').map((row) => ({
    ruleId: safeNumber(row.ruleId),
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    severity: row.severity,
    penaltyPoints: safeNumber(row.penaltyPoints),
    description: row.description,
    enabled: true
  }));
  const runtime = readRuntimeData();
  let submissions = Array.isArray(runtime?.submissions) ? runtime.submissions : createSeedSubmissions();
  let rules = Array.isArray(runtime?.rules) ? runtime.rules : csvRules;

  function persist() {
    fs.mkdirSync(runtimeDirectory, { recursive: true });
    const temporaryFile = `${runtimeFile}.tmp`;
    fs.writeFileSync(temporaryFile, JSON.stringify({ submissions, rules }, null, 2), 'utf8');
    fs.renameSync(temporaryFile, runtimeFile);
  }

  return {
    users,
    findUser(username) {
      return users.find((user) => user.username === username) ?? null;
    },
    listSubmissions() {
      return [...submissions];
    },
    listSubmissionsForUser(userId) {
      return submissions.filter((submission) => submission.userId === userId);
    },
    findSubmission(submissionId) {
      return submissions.find((submission) => submission.submissionId === submissionId) ?? null;
    },
    findSubmissionByHash(fileHash) {
      return submissions.find((submission) => submission.fileHash === fileHash) ?? null;
    },
    createSubmission(submission) {
      submissions = [{
        ...submission,
        submissionId: submission.submissionId ?? `SUB-${randomUUID()}`,
        submittedAt: submission.submittedAt ?? new Date().toISOString()
      }, ...submissions];
      persist();
      return submissions[0];
    },
    updateGrade(submissionId, grade) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      submission.score = {
        ...submission.score,
        ...grade,
        updatedAt: new Date().toISOString(),
        manuallyEdited: true
      };
      persist();
      return submission;
    },
    updateFeedback(submissionId, feedback) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      submission.manualFeedback = feedback;
      persist();
      return submission;
    },
    listRules() {
      return [...rules];
    },
    updateRule(ruleCode, changes) {
      const rule = rules.find((item) => item.ruleCode === ruleCode);
      if (!rule) return null;
      Object.assign(rule, changes);
      persist();
      return rule;
    },
    createClassReport() {
      const students = users.filter((user) => user.role.toLowerCase() === 'student');
      const reportRows = students.map((student) => {
        const studentSubmissions = submissions.filter((submission) => submission.userId === student.userId);
        const graded = studentSubmissions.filter((submission) => Number.isFinite(submission.score?.totalScore));
        const average = graded.length
          ? graded.reduce((sum, submission) => sum + submission.score.totalScore, 0) / graded.length
          : null;
        return {
          userId: student.userId,
          fullName: student.fullName,
          submissionCount: studentSubmissions.length,
          gradedCount: graded.length,
          averageScore: average === null ? null : Number(average.toFixed(2)),
          scores: graded.map((submission) => ({
            submissionId: submission.submissionId,
            score: submission.score.totalScore,
            submittedAt: submission.submittedAt
          }))
        };
      });
      const allScores = reportRows.flatMap((row) => row.scores.map((item) => item.score));

      return {
        studentCount: students.length,
        submissionCount: submissions.length,
        gradedSubmissionCount: allScores.length,
        classAverage: allScores.length
          ? Number((allScores.reduce((sum, score) => sum + score, 0) / allScores.length).toFixed(2))
          : null,
        students: reportRows
      };
    }
  };
}