import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { AnalysisQueue, getRetryDelay, getSubmissionPriority } from '../src/analysis-queue.js';

test('submission priority reflects assignment deadlines', () => {
  const now = Date.parse('2026-11-04T00:00:00.000Z');
  assert.equal(getSubmissionPriority(null, now), 10);
  assert.equal(getSubmissionPriority({ dueAt: '2026-11-04T12:00:00.000Z' }, now), 1);
  assert.equal(getSubmissionPriority({ dueAt: '2026-11-08T00:00:00.000Z' }, now), 5);
  assert.equal(getSubmissionPriority({ dueAt: '2026-11-20T00:00:00.000Z' }, now), 10);
});

test('analysis retries use capped exponential backoff', () => {
  assert.equal(getRetryDelay(1), 1000);
  assert.equal(getRetryDelay(2), 2000);
  assert.equal(getRetryDelay(6), 30000);
  assert.throws(() => getRetryDelay(0), RangeError);
});

test('Redis reserves higher-priority analysis before lower-priority work and retries jobs', async (context) => {
  const queue = new AnalysisQueue({
    prefix: `aita:test:${randomUUID()}`,
    url: process.env.REDIS_URL || 'redis://127.0.0.1:6379'
  });
  try {
    await queue.ping();
  } catch (error) {
    if (process.env.CI || process.env.REDIS_URL) throw error;
    context.skip('Set REDIS_URL or start local Redis to run the queue integration test.');
    return;
  }

  try {
    await queue.enqueue({ submissionId: 'lower-priority', priority: 10 });
    await queue.enqueue({ submissionId: 'higher-priority', priority: 1 });
    const first = await queue.reserve();
    assert.equal(first.type, 'job');
    assert.equal(first.job.submissionId, 'higher-priority');
    assert.equal(first.job.attemptsMade, 1);

    assert.equal(await queue.retry(first.job, 0), true);
    const retried = await queue.reserve();
    assert.equal(retried.job.submissionId, 'higher-priority');
    assert.equal(retried.job.attemptsMade, 2);
    await queue.acknowledge(retried.job.submissionId);

    const next = await queue.reserve();
    assert.equal(next.job.submissionId, 'lower-priority');
    await queue.acknowledge(next.job.submissionId);
  } finally {
    await queue.clear();
  }
});
