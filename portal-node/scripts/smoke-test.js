import assert from 'node:assert/strict';

const baseUrl = process.env.PORTAL_URL || 'http://localhost:3000';

async function login(username, passwordHash, role) {
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, passwordHash, role })
  });
  assert.equal(response.status, 200, `${role} login failed`);
  return response.json();
}

const student = await login('landang93', 'hash_landang93', 'Student');
const classReportAttempt = await fetch(`${baseUrl}/api/teacher/report`, {
  headers: { Authorization: `Bearer ${student.token}` }
});
assert.equal(classReportAttempt.status, 403, 'Student must not access teacher reports');

const className = `PortalSmoke${Date.now()}`;
const form = new FormData();
form.append(
  'sourceFile',
  new Blob([`public class ${className} { public Object value() { return null; } }`]),
  `${className}.java`
);
const uploadResponse = await fetch(`${baseUrl}/api/student/submissions`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${student.token}` },
  body: form
});
assert.equal(uploadResponse.status, 201, 'Student upload failed');
const { submission } = await uploadResponse.json();
assert.equal(submission.analysis.syntaxValid, true);
assert.equal(submission.analysis.violations[0].ruleCode, 'NPE');

const teacher = await login('anhvu95', 'hash_anhvu95', 'Teacher');
const classListResponse = await fetch(`${baseUrl}/api/teacher/submissions`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
const classList = await classListResponse.json();
assert.ok(classList.submissions.some((item) => item.submissionId === submission.submissionId));

const gradeResponse = await fetch(`${baseUrl}/api/teacher/submissions/${submission.submissionId}/grade`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ totalScore: 9, deduction: 1, gradingNotes: 'Reviewed by teacher.' })
});
assert.equal(gradeResponse.status, 200, 'Teacher grade update failed');

const feedbackResponse = await fetch(`${baseUrl}/api/teacher/submissions/${submission.submissionId}/feedback`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ feedback: 'Review the nullable return value.' })
});
assert.equal(feedbackResponse.status, 200, 'Teacher feedback update failed');

const gradesResponse = await fetch(`${baseUrl}/api/student/grades`, {
  headers: { Authorization: `Bearer ${student.token}` }
});
const { grades } = await gradesResponse.json();
assert.ok(grades.some((grade) => grade.submissionId === submission.submissionId && grade.totalScore === 9));

console.log('Smoke test passed: auth, role access, upload, analysis, teacher grade/feedback, student grade visibility.');