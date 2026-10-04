import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzeJavaSource, sha256 } from '../src/ai-engine.js';
import { createAuth } from '../src/auth.js';

test('SHA-256 is stable for source text', () => {
  assert.equal(sha256('class Demo {}'), '6b0bc56d86c65d0bfcd9345491723d348e6f9e3375cb09be21aafc8c1b3aea08');
});

test('Java syntax is parsed and AST summary is returned', async () => {
  process.env.AI_PROVIDER = 'disabled';
  const result = await analyzeJavaSource('public class Demo { public void run() {} }', []);
  assert.equal(result.syntaxValid, true);
  assert.equal(result.astSummary.root, 'compilationUnit');
  assert.ok(result.astSummary.grammarNodeCount > 0);
  assert.equal(result.totalScore, 10);
});

test('rule engine reports configured findings and deducts points', async () => {
  process.env.AI_PROVIDER = 'disabled';
  const result = await analyzeJavaSource('public class Demo {\n  public Object value() { return null; }\n}', [
    { ruleCode: 'NPE', ruleName: 'Null return', severity: 'High', penaltyPoints: 2, enabled: true }
  ]);
  assert.equal(result.syntaxValid, true);
  assert.equal(result.violations.length, 1);
  assert.equal(result.violations[0].lineNumber, 2);
  assert.equal(result.totalScore, 8);
});

test('invalid Java is marked for review', async () => {
  process.env.AI_PROVIDER = 'disabled';
  const result = await analyzeJavaSource('public class {', []);
  assert.equal(result.syntaxValid, false);
  assert.match(result.aiFeedback, /could not be parsed/i);
});

test('login enforces the selected role', () => {
  const auth = createAuth({
    findUser: (username) => username === 'student1'
      ? { userId: 'U1', username, passwordHash: 'hash_demo', fullName: 'Demo Student', role: 'Student' }
      : null
  });
  assert.equal(auth.login({ username: 'student1', passwordHash: 'hash_demo', role: 'Teacher' }), null);
  const session = auth.login({ username: 'student1', passwordHash: 'hash_demo', role: 'Student' });
  assert.equal(session.user.role, 'Student');
  assert.ok(auth.getUser(session.token));
});