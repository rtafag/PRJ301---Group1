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

const student = await login('dungdang442', 'hash_dungdang442', 'Student');
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

const teacher = await login('sondang619', 'hash_sondang619', 'Teacher');
const coursesResponse = await fetch(`${baseUrl}/api/teacher/courses`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(coursesResponse.status, 200, 'Teacher course list failed');
const { courses, semesters } = await coursesResponse.json();
assert.equal(semesters.length, 6, 'Semester selector should contain six periods');
assert.ok(courses.length > 0, 'Teacher should have assigned courses');
assert.ok(courses.every((course) => course.active), 'All assigned courses should be active');

const firstCourse = courses[0];
const firstCourseResponse = await fetch(`${baseUrl}/api/teacher/course-grades?courseCode=${firstCourse.courseCode}&semester=fall-2026`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(firstCourseResponse.status, 200, 'Course grades failed to load');
const { grades: firstCourseGrades } = await firstCourseResponse.json();
assert.ok(firstCourseGrades.length > 0, 'Fall 2026 should contain course grades');
assert.ok(firstCourseGrades.every((grade) => grade.active), 'All students should be active');
assert.equal(firstCourse.courseCode, 'JPE255', 'The first mapped teacher should teach JPE255');

const academicStudent = await login('dungdang442', 'hash_dungdang442', 'Student');
const studentCourseGradesResponse = await fetch(`${baseUrl}/api/student/course-grades`, {
  headers: { Authorization: `Bearer ${academicStudent.token}` }
});
assert.equal(studentCourseGradesResponse.status, 200, 'CSV-linked student grades failed to load');
const { courseGrades: academicStudentGrades } = await studentCourseGradesResponse.json();
assert.ok(academicStudentGrades.length > 0, 'CSV-linked student should see their enrolled course grades');
assert.ok(academicStudentGrades.every((grade) => grade.studentId === 'HE001'));
const studentCoursesResponse = await fetch(`${baseUrl}/api/student/courses`, {
  headers: { Authorization: `Bearer ${academicStudent.token}` }
});
assert.equal(studentCoursesResponse.status, 200, 'Student course list failed');
const { courses: academicStudentCourses, semesters: studentSemesters } = await studentCoursesResponse.json();
assert.equal(studentSemesters.length, 6);
assert.deepEqual(academicStudentCourses.map((course) => course.courseCode).sort(), academicStudentGrades.map((grade) => grade.courseCode).sort());

const savedGrade = firstCourseGrades[0];
assert.equal(savedGrade.studentId, 'HE001', 'The first course roster should start with the first student account');
const courseGradeUpdateResponse = await fetch(`${baseUrl}/api/teacher/course-grades/${savedGrade.studentId}`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    courseCode: firstCourse.courseCode,
    semester: 'fall-2026',
    totalScore: savedGrade.totalScore,
    deduction: savedGrade.deduction,
    gradingNotes: savedGrade.gradingNotes
  })
});
assert.equal(courseGradeUpdateResponse.status, 200, 'Teacher course grade update failed');
const updatedCourseGradesResponse = await fetch(`${baseUrl}/api/teacher/course-grades?courseCode=${firstCourse.courseCode}&semester=fall-2026`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
const { grades: updatedCourseGrades } = await updatedCourseGradesResponse.json();
assert.equal(updatedCourseGrades[0].totalScore, savedGrade.totalScore, 'Saved course grade should remain available');

const courseFeedbackUrl = `${baseUrl}/api/teacher/course-grades/${savedGrade.studentId}/feedback`;
const courseFeedbackResponse = await fetch(courseFeedbackUrl, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    courseCode: firstCourse.courseCode,
    semester: 'fall-2026',
    manualFeedback: 'Smoke test feedback.'
  })
});
assert.equal(courseFeedbackResponse.status, 200, 'Course feedback update failed');
const courseFeedbackGradesResponse = await fetch(`${baseUrl}/api/teacher/course-grades?courseCode=${firstCourse.courseCode}&semester=fall-2026`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
const { grades: courseFeedbackGrades } = await courseFeedbackGradesResponse.json();
assert.equal(courseFeedbackGrades.find((grade) => grade.studentId === savedGrade.studentId).manualFeedback, 'Smoke test feedback.');
const feedbackStudent = await login('dungdang442', 'hash_dungdang442', 'Student');
const feedbackStudentGradesResponse = await fetch(`${baseUrl}/api/student/course-grades`, {
  headers: { Authorization: `Bearer ${feedbackStudent.token}` }
});
const { courseGrades: feedbackStudentGrades } = await feedbackStudentGradesResponse.json();
assert.equal(feedbackStudentGrades.find((grade) => grade.courseCode === firstCourse.courseCode).manualFeedback, 'Smoke test feedback.');
await fetch(courseFeedbackUrl, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    courseCode: firstCourse.courseCode,
    semester: 'fall-2026',
    manualFeedback: savedGrade.manualFeedback ?? ''
  })
});

const assignmentCreateResponse = await fetch(`${baseUrl}/api/teacher/assignments`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    title: 'Portal smoke assignment',
    courseCode: firstCourse.courseCode,
    description: 'Temporary assignment for CRUD verification.',
    dueAt: new Date(Date.now() + 86400000).toISOString()
  })
});
assert.equal(assignmentCreateResponse.status, 201, 'Teacher assignment creation failed');
const { assignment: createdAssignment } = await assignmentCreateResponse.json();
assert.equal(createdAssignment.status, 'Active', 'Future deadline should be active');
const studentAssignmentsResponse = await fetch(`${baseUrl}/api/student/assignments`, {
  headers: { Authorization: `Bearer ${academicStudent.token}` }
});
assert.equal(studentAssignmentsResponse.status, 200, 'Student assignment list failed');
const { assignments: studentAssignments } = await studentAssignmentsResponse.json();
const studentAssignment = studentAssignments.find((assignment) => assignment.assignmentId === createdAssignment.assignmentId);
assert.ok(studentAssignment, 'Enrolled student should see the course assignment');
assert.equal(studentAssignment.isRead, false, 'New course assignment should be unread');
const markAssignmentReadResponse = await fetch(`${baseUrl}/api/student/assignments/${createdAssignment.assignmentId}/read`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${academicStudent.token}` }
});
assert.equal(markAssignmentReadResponse.status, 200, 'Student could not mark assignment as read');
const refreshedStudentAssignmentsResponse = await fetch(`${baseUrl}/api/student/assignments`, {
  headers: { Authorization: `Bearer ${academicStudent.token}` }
});
const { assignments: refreshedStudentAssignments } = await refreshedStudentAssignmentsResponse.json();
assert.equal(refreshedStudentAssignments.find((assignment) => assignment.assignmentId === createdAssignment.assignmentId).isRead, true);
const extendedDueAt = new Date(new Date(createdAssignment.dueAt).valueOf() + 86400000).toISOString();
const assignmentExtendResponse = await fetch(`${baseUrl}/api/teacher/assignments/${createdAssignment.assignmentId}`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ dueAt: extendedDueAt })
});
assert.equal(assignmentExtendResponse.status, 200, 'Assignment deadline update failed');
const { assignment: extendedAssignment } = await assignmentExtendResponse.json();
assert.equal(extendedAssignment.dueAt, extendedDueAt, 'Assignment extension should update the deadline');
const assignmentExpireResponse = await fetch(`${baseUrl}/api/teacher/assignments/${createdAssignment.assignmentId}`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ dueAt: new Date(Date.now() - 60000).toISOString() })
});
assert.equal(assignmentExpireResponse.status, 200, 'Assignment deadline reduction failed');
const { assignment: expiredAssignment } = await assignmentExpireResponse.json();
assert.equal(expiredAssignment.status, 'Expired', 'Past deadline should be expired');
const assignmentDeleteResponse = await fetch(`${baseUrl}/api/teacher/assignments/${createdAssignment.assignmentId}`, {
  method: 'DELETE', headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(assignmentDeleteResponse.status, 204, 'Teacher assignment deletion failed');

const secondCourse = courses[1];
if (secondCourse) {
  const secondCourseResponse = await fetch(`${baseUrl}/api/teacher/course-grades?courseCode=${secondCourse.courseCode}&semester=fall-2026`, {
    headers: { Authorization: `Bearer ${teacher.token}` }
  });
  const { grades: secondCourseGrades } = await secondCourseResponse.json();
  assert.ok(secondCourseGrades.length > 0, 'Teacher should have students in each assigned course');
  assert.ok(secondCourseGrades.every((grade) => Number.isFinite(grade.totalScore)), 'Course roster should include scores');
}

const emptySemesterResponse = await fetch(`${baseUrl}/api/teacher/course-grades?courseCode=${firstCourse.courseCode}&semester=summer-2026`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
const { grades: emptySemesterGrades } = await emptySemesterResponse.json();
assert.equal(emptySemesterGrades.length, 0, 'Only Fall 2026 should contain demo course grades');

const unauthorizedCourseResponse = await fetch(`${baseUrl}/api/teacher/course-grades?courseCode=NOT-A-COURSE&semester=fall-2026`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(unauthorizedCourseResponse.status, 403, 'Teacher must not access unassigned courses');

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

console.log('Smoke test passed: auth, course/semester access, course grades and feedback, assignment create/edit/expire/delete, upload, analysis, and student grade visibility.');