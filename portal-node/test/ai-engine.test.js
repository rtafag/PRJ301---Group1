import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse } from 'csv-parse/sync';
import { analyzeJavaSource, sha256, extractSlicedContext, buildPromptPayload } from '../src/ai-engine.js';
import { createAuth } from '../src/auth.js';
import { createStore } from '../src/store.js';

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

test('login accepts seeded SHA-256 password digests', () => {
  const user = {
    userId: 'HE001',
    username: 'student1',
    passwordHash: 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f',
    fullName: 'Demo Student',
    role: 'Student'
  };
  const auth = createAuth({ findUser: (username) => username === user.username ? user : null });

  assert.ok(auth.login({ username: user.username, passwordHash: 'password123' }));
  assert.equal(auth.login({ username: user.username, passwordHash: 'wrong-password' }), null);
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

test('store loads seeded rules including NPE', () => {
  const store = createStore({ runtimeData: {} });
  const rules = store.listRules();

  assert.ok(rules.length > 0);
  assert.ok(rules.some((rule) => rule.ruleCode === 'NPE'));
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

test('context slicing extracts only 5 lines around the violation and does not send raw whole file', () => {
  const longSource = [
    'public class BigFile {',
    ...Array.from({ length: 50 }, (_, i) => `  // unrelated line ${i + 1}`),
    '  public void buggy() {',
    '    int x = 10 / 0;',
    '  }',
    ...Array.from({ length: 50 }, (_, i) => `  // trailing line ${i + 1}`),
    '}'
  ].join('\n');

  const findings = [{
    ruleCode: 'DIV_ZERO',
    ruleName: 'Division by zero',
    lineNumber: 53,
    suggestedFix: 'Avoid dividing by constant zero.'
  }];

  const sliced = extractSlicedContext(longSource, findings);
  assert.ok(sliced.includes('>> 53:     int x = 10 / 0;'));
  assert.ok(sliced.includes('51:'));
  assert.ok(sliced.includes('55:'));
  // Must NOT include lines far away like line 5 or line 90
  assert.ok(!sliced.includes('unrelated line 5\n'));
  assert.ok(!sliced.includes('trailing line 45\n'));
  assert.ok(sliced.length < longSource.length * 0.2, 'Sliced context should be less than 20% of original file length');
});

test('buildPromptPayload formats prompt with context slicing and anti-hallucination structure', () => {
  const source = 'public class Test {\n  void f() {\n    String a = "hi";\n    if (a == "hi") {}\n  }\n}';
  const findings = [{
    ruleCode: 'STR_EQ',
    ruleName: 'String comparison',
    lineNumber: 4,
    suggestedFix: 'Use .equals() instead of == for String comparison.'
  }];

  const payload = buildPromptPayload(source, findings);
  assert.ok(payload.includes('Context Slicing'));
  assert.ok(payload.includes('[STR_EQ]'));
  assert.ok(payload.includes('Dòng 4'));
  assert.ok(payload.includes('>> 4:     if (a == "hi") {}'));
});
