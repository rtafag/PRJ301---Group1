import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse } from 'csv-parse/sync';
import { analyzeJavaSource, sha256 } from '../src/ai-engine.js';
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