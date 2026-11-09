import test from 'node:test';
import assert from 'node:assert/strict';
import 'dotenv/config';
import { createSqlRuntimeRepository } from '../src/sql-store.js';

test('SQL Server 6-Table Persistence Verification (TV2 Deliverable)', async (t) => {
  if (!process.env.DB_USER && !process.env.DB_PASSWORD) {
    process.env.DB_USER = 'sa';
    process.env.DB_PASSWORD = 'password123';
  }

  let repo;
  try {
    repo = await createSqlRuntimeRepository();
  } catch (err) {
    t.skip(`Skipping live SQL test: ${err.message}`);
    return;
  }

  await t.test('1. load() reconstructs submissions and rules from 6 SQL tables', async () => {
    const data = await repo.load();
    assert.ok(Array.isArray(data.submissions), 'Submissions must be an array');
    assert.ok(data.submissions.length >= 200, `Expected >= 200 submissions, got ${data.submissions.length}`);
    assert.ok(Array.isArray(data.rules), 'Rules must be an array');
    assert.ok(data.rules.length >= 6, `Expected >= 6 rules, got ${data.rules.length}`);

    const sub = data.submissions.find((s) => s.submissionId === 'SUB0001');
    assert.ok(sub, 'SUB0001 must exist');
    assert.ok(sub.score, 'SUB0001 must have a Score');
    assert.ok(sub.analysis, 'SUB0001 must have an AnalysisResult');
  });

  await t.test('2. save() writes updates across Submission, Score, AnalysisResult, and RuleViolation', async () => {
    const data = await repo.load();
    const testSubId = 'SUB_TEST_SQL6';

    const testSubmission = {
      submissionId: testSubId,
      userId: 'HE001',
      fileUrl: 'code/TestSync.java',
      fileHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      status: 'Graded',
      submittedAt: new Date().toISOString(),
      score: {
        totalScore: 9.7,
        deduction: 0.3,
        gradingNotes: 'Verified 6-table relational persistence',
        updatedAt: new Date().toISOString()
      },
      analysis: {
        astJson: '{"type":"UnitCompilation","status":"PASS"}',
        aiFeedback: 'Excellent algorithmic structure.',
        executionTimeMs: 95,
        violations: [
          {
            ruleId: 1,
            fileName: 'TestSync.java',
            lineNumber: 12,
            suggestedFix: 'Add Javadoc header'
          }
        ]
      }
    };

    data.submissions = [testSubmission, ...data.submissions];
    await repo.save(data);

    // Verify it re-loads directly from the 6 SQL tables
    const reloaded = await repo.load();
    const retrieved = reloaded.submissions.find((s) => s.submissionId === testSubId);
    assert.ok(retrieved, 'New submission must be loaded from SQL');
    assert.equal(retrieved.userId, 'HE001');
    assert.equal(retrieved.status, 'Graded');
    assert.ok(Math.abs(retrieved.score.totalScore - 9.7) < 0.05, 'Score must match');
    assert.equal(retrieved.analysis.violations.length, 1, 'Violations count must match');

    // Clean up test entry
    data.submissions = data.submissions.filter((s) => s.submissionId !== testSubId);
    await repo.save(data);
  });

  await repo.close();
});
