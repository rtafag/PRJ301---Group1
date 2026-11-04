import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse } from 'csv-parse/sync';
import { analyzeJavaSource, sha256 } from '../src/ai-engine.js';
import { createAuth } from '../src/auth.js';
import { createStore } from '../src/store.js';

async function withAiConfiguration(configuration, run) {
  const previousEnvironment = Object.fromEntries(
    Object.keys(configuration).map((key) => [key, process.env[key]])
  );
  const previousFetch = globalThis.fetch;
  for (const [key, value] of Object.entries(configuration)) process.env[key] = value;
  try {
    return await run();
  } finally {
    for (const [key, value] of Object.entries(previousEnvironment)) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
    globalThis.fetch = previousFetch;
  }
}

test('SHA-256 is stable for source text', () => {
  assert.equal(sha256('class Demo {}'), '6b0bc56d86c65d0bfcd9345491723d348e6f9e3375cb09be21aafc8c1b3aea08');
});

test('OpenAI prompt grounds feedback in findings and source evidence', async () => {
  await withAiConfiguration({
    AI_PROVIDER: 'openai',
    OPENAI_API_KEY: 'test-key',
    OPENAI_MODEL: 'test-model'
  }, async () => {
    let requestBody;
    globalThis.fetch = async (url, options) => {
      assert.equal(url, 'https://api.openai.com/v1/chat/completions');
      requestBody = JSON.parse(options.body);
      return new Response(JSON.stringify({
        choices: [{ message: { content: 'Có một cảnh báo cần kiểm tra.' } }]
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };

    const source = 'public class Demo {\n  public Object value() { return null; }\n}';
    const result = await analyzeJavaSource(source, [
      { ruleCode: 'NPE', ruleName: 'Null return', severity: 'High', penaltyPoints: 2, enabled: true }
    ]);

    assert.equal(result.aiFeedback, 'Có một cảnh báo cần kiểm tra.');
    assert.equal(result.providerWarning, null);
    assert.equal(result.violations[0].lineNumber, 2);
    assert.equal(result.violations[0].evidence, 'public Object value() { return null; }');
    assert.equal(requestBody.model, 'test-model');
    assert.equal(requestBody.temperature, 0.1);
    assert.match(requestBody.messages[0].content, /heuristic/);
    assert.match(requestBody.messages[0].content, /false positive/);
    assert.match(requestBody.messages[0].content, /không.*điểm số/);
    assert.deepEqual(JSON.parse(requestBody.messages[1].content).findings, result.violations);
  });
});

test('Gemini receives the same evidence-based prompt', async () => {
  await withAiConfiguration({
    AI_PROVIDER: 'gemini',
    GEMINI_API_KEY: 'test-key',
    GEMINI_MODEL: 'test-model'
  }, async () => {
    let requestBody;
    globalThis.fetch = async (url, options) => {
      assert.match(String(url), /models\/test-model:generateContent/);
      assert.equal(new URL(url).searchParams.get('key'), 'test-key');
      requestBody = JSON.parse(options.body);
      return new Response(JSON.stringify({
        candidates: [{ content: { parts: [{ text: 'Không phát hiện lỗi chắc chắn.' }] } }]
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    };

    const result = await analyzeJavaSource('public class Demo {}', []);
    assert.equal(result.aiFeedback, 'Không phát hiện lỗi chắc chắn.');
    assert.match(requestBody.systemInstruction.parts[0].text, /bằng tiếng Việt/);
    assert.equal(JSON.parse(requestBody.contents[0].parts[0].text).source, 'public class Demo {}');
  });
});

test('selected AI provider configuration failures are visible in analysis results', async () => {
  await withAiConfiguration({ AI_PROVIDER: 'openai', OPENAI_API_KEY: '' }, async () => {
    globalThis.fetch = async () => {
      throw new Error('fetch should not run without an API key');
    };
    const result = await analyzeJavaSource('public class Demo {}', []);
    assert.equal(result.aiFeedback, 'Static analysis found no configured rule violations.');
    assert.match(result.providerWarning, /OPENAI_API_KEY is not configured/);
  });
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
  assert.equal(result.violations[0].evidence, 'public Object value() { return null; }');
  assert.equal(result.totalScore, 8);
});

test('invalid Java is marked for review', async () => {
  process.env.AI_PROVIDER = 'disabled';
  const result = await analyzeJavaSource('public class {', []);
  assert.equal(result.syntaxValid, false);
  assert.match(result.aiFeedback, /could not be parsed/i);
});

test('login determines the role from the matching account credentials', () => {
  const auth = createAuth({
    findUser: (username) => ({
      student1: { userId: 'U1', username, passwordHash: 'student_hash', fullName: 'Demo Student', role: 'Student' },
      teacher1: { userId: 'U2', username, passwordHash: 'teacher_hash', fullName: 'Demo Teacher', role: 'Teacher' }
    })[username] ?? null
  });
  const studentSession = auth.login({ username: 'student1', passwordHash: 'student_hash' });
  const teacherSession = auth.login({ username: 'teacher1', passwordHash: 'teacher_hash' });
  assert.equal(studentSession.user.role, 'Student');
  assert.equal(teacherSession.user.role, 'Teacher');
  assert.equal(auth.login({ username: 'student1', passwordHash: 'wrong_password' }), null);
  assert.ok(auth.getUser(studentSession.token));
});

test('password reset requires matching email and User ID and updates login credentials', () => {
  const user = {
    userId: 'HE001', username: 'student1', email: 'student@example.com',
    passwordHash: 'student_hash', fullName: 'Demo Student', role: 'Student'
  };
  const auth = createAuth({
    findUser: (username) => user.username === username ? user : null,
    resetPassword: (email, confirmationKey, passwordHash) => {
      if (email !== user.email || confirmationKey !== user.userId) return false;
      user.passwordHash = passwordHash;
      return true;
    }
  });

  assert.equal(auth.resetPassword({ email: user.email, confirmationKey: 'WRONG', newPassword: 'new-password' }), false);
  assert.equal(auth.resetPassword({ email: user.email, confirmationKey: user.userId, newPassword: 'short' }), false);
  assert.equal(auth.resetPassword({ email: user.email, confirmationKey: user.userId, newPassword: 'new-password' }), true);
  assert.equal(auth.login({ username: user.username, passwordHash: 'student_hash' }), null);
  assert.ok(auth.login({ username: user.username, passwordHash: 'new-password' }));
});

test('store sends changed runtime data to its configured persistence adapter', async () => {
  const savedSnapshots = [];
  const store = createStore({
    runtimeData: {},
    saveRuntimeData: async (runtimeData) => savedSnapshots.push(runtimeData)
  });

  store.updateUserProfile('HE001', { fullName: 'Updated Student', email: 'updated@example.com' });
  await store.flushPersistence();

  assert.equal(savedSnapshots.length, 1);
  assert.equal(savedSnapshots[0].profileOverrides.HE001.fullName, 'Updated Student');
});

test('course students are evenly split among assigned teachers', () => {
  const store = createStore();
  const teachers = parse(readFileSync(new URL('../../data/teachers.csv', import.meta.url), 'utf8'), {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true
  });
  const teacherUserIds = teachers
    .map((teacher, index) => ({
      teacher,
      userId: `GV${String(index + 1).padStart(4, '0')}`
    }))
    .filter(({ teacher }) => teacher.CourseCode.split('|').includes('JPE255'))
    .map(({ userId }) => userId);
  const rosters = teacherUserIds.map((userId) => store.listCourseGrades(userId, 'JPE255', 'fall-2026'));
  const assignedStudentIds = rosters.flat().map((grade) => grade.studentId).sort();
  const students = parse(readFileSync(new URL('../../data/students.csv', import.meta.url), 'utf8'), {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true
  });
  const expectedStudentIds = students
    .filter((student) => student.CourseCode.split('|').includes('JPE255'))
    .map((student) => `HE${String(students.indexOf(student) + 1).padStart(3, '0')}`)
    .sort();
  const rosterCounts = rosters.map((roster) => roster.length);

  assert.deepEqual(assignedStudentIds, expectedStudentIds);
  assert.equal(new Set(assignedStudentIds).size, expectedStudentIds.length);
  assert.ok(Math.max(...rosterCounts) - Math.min(...rosterCounts) <= 1);

  const users = parse(readFileSync(new URL('../../data/users.csv', import.meta.url), 'utf8'), {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true
  });
  assert.equal(users.length, 1100);
  assert.ok(users.some((user) => user.userId === 'GV0001' && user.username === 'sondang619'));
  assert.ok(users.some((user) => user.userId === 'HE001' && user.username === 'dungdang442'));
  assert.ok(users.every((user) => user.role === 'Teacher' ? /^GV\d{4}$/.test(user.userId) : /^HE\d{3,}$/.test(user.userId)));
  assert.ok(users.some((user) => user.userId === 'GV0060' && user.username === 'anhle536gv0060'));
  assert.ok(users.some((user) => user.userId === 'HE641' && user.username === 'anhle536he641'));
});

test('each enrolled course has a distinct demo score for the student', () => {
  const store = createStore();
  const students = parse(readFileSync(new URL('../../data/students.csv', import.meta.url), 'utf8'), {
    bom: true,
    columns: true,
    skip_empty_lines: true,
    trim: true
  });
  const student = students.find((row) => row.CourseCode.split('|').length > 1);
  const studentIndex = students.indexOf(student);
  const studentId = `HE${String(studentIndex + 1).padStart(3, '0')}`;
  const courseGrades = store.listCourseGradesForStudent(studentId);

  assert.equal(courseGrades.length, student.CourseCode.split('|').length);
  assert.equal(new Set(courseGrades.map((grade) => grade.totalScore)).size, courseGrades.length);
});

test('rule engine detects missing comments, long methods, and duplicate method bodies', async () => {
  process.env.AI_PROVIDER = 'disabled';
  const source = `public class Review {
  public int first() {
    int total = 0;
${'    total += 1;\n'.repeat(42)}    return total;
  }
  public int second() {
    int total = 0;
${'    total += 1;\n'.repeat(42)}    return total;
  }
}`;
  const rules = [
    { ruleCode: 'COMMENT', ruleName: 'Missing comment', severity: 'Low', penaltyPoints: 0.5, enabled: true },
    { ruleCode: 'LONG_METHOD', ruleName: 'Long method', severity: 'Medium', penaltyPoints: 1, enabled: true },
    { ruleCode: 'DUPLICATE_CODE', ruleName: 'Duplicate code', severity: 'Medium', penaltyPoints: 1.5, enabled: true }
  ];
  const result = await analyzeJavaSource(source, rules);
  assert.equal(result.syntaxValid, true);
  assert.ok(result.violations.some((finding) => finding.ruleCode === 'COMMENT' && finding.lineNumber === 2));
  assert.ok(result.violations.some((finding) => finding.ruleCode === 'LONG_METHOD' && finding.lineNumber === 2));
  assert.ok(result.violations.some((finding) => finding.ruleCode === 'DUPLICATE_CODE' && finding.lineNumber > 2));
  assert.ok(result.totalScore < 10);
});