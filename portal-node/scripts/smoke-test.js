import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse } from 'csv-parse/sync';

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
const attachmentBytes = Uint8Array.from([0x25, 0x50, 0x44, 0x46, 0x00, Date.now() % 256, 0xff, 0x0a]);
const attachmentName = `attachment-${Date.now()}.pdf`;
const attachmentForm = new FormData();
attachmentForm.append('sourceFile', new Blob([attachmentBytes], { type: 'application/pdf' }), attachmentName);
const attachmentUploadResponse = await fetch(`${baseUrl}/api/student/submissions`, {
  method: 'POST', headers: { Authorization: `Bearer ${student.token}` }, body: attachmentForm
});
assert.equal(attachmentUploadResponse.status, 201, 'Student should be able to submit non-Java files');
const { submission: attachmentSubmission } = await attachmentUploadResponse.json();
assert.equal(attachmentSubmission.status, 'Submitted', 'Non-Java files should be recorded without Java analysis');
assert.equal(attachmentSubmission.analysis, null);
assert.equal(attachmentSubmission.score, null);
const studentAttachmentResponse = await fetch(`${baseUrl}/api/student/submissions/${attachmentSubmission.submissionId}/file`, {
  headers: { Authorization: `Bearer ${student.token}` }
});
assert.equal(studentAttachmentResponse.status, 200, 'Student should be able to download their original attachment');
assert.deepEqual(new Uint8Array(await studentAttachmentResponse.arrayBuffer()), attachmentBytes);
const unsupportedSandboxResponse = await fetch(`${baseUrl}/api/student/submissions/${attachmentSubmission.submissionId}/run`, {
  method: 'POST', headers: { Authorization: `Bearer ${student.token}` }
});
assert.equal(unsupportedSandboxResponse.status, 415, 'Non-Java files must not be sent to the Java sandbox');

const classManagementResponse = await fetch(`${baseUrl}/api/teacher/classes`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(classManagementResponse.status, 200, 'Teacher class-management list failed');
const { classes: teacherClasses, students: allStudents, teachers: allTeachers } = await classManagementResponse.json();
const managedClass = teacherClasses.find((item) => item.courseCode === firstCourse.courseCode);
assert.ok(managedClass?.roster.length, 'Teacher should see the assigned class roster');
const unassignedStudent = allStudents.find((item) => !item.courseCodes.includes(firstCourse.courseCode));
assert.ok(unassignedStudent, 'An unenrolled student should be available for enrollment testing');
const enrollResponse = await fetch(`${baseUrl}/api/teacher/classes/${firstCourse.courseCode}/enrollments/${unassignedStudent.studentId}`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ enrolled: true })
});
assert.equal(enrollResponse.status, 204, 'Teacher should enroll a student');
const removeEnrollmentResponse = await fetch(`${baseUrl}/api/teacher/classes/${firstCourse.courseCode}/enrollments/${unassignedStudent.studentId}`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ enrolled: false })
});
assert.equal(removeEnrollmentResponse.status, 204, 'Teacher should remove a student');
const alternateTeacher = allTeachers.find((item) => !managedClass.teacherIds.includes(item.userId));
if (alternateTeacher) {
  const expandedTeachers = [...managedClass.teacherIds, alternateTeacher.userId];
  const assignTeacherResponse = await fetch(`${baseUrl}/api/teacher/classes/${firstCourse.courseCode}/teachers`, {
    method: 'PATCH', headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ teacherIds: expandedTeachers })
  });
  assert.equal(assignTeacherResponse.status, 200, 'Teacher should update class teacher assignments');
  const restoreTeacherResponse = await fetch(`${baseUrl}/api/teacher/classes/${firstCourse.courseCode}/teachers`, {
    method: 'PATCH', headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ teacherIds: managedClass.teacherIds })
  });
  assert.equal(restoreTeacherResponse.status, 200, 'Teacher assignments should be restorable');
}
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
const feedbackNotificationsResponse = await fetch(`${baseUrl}/api/me/notifications`, {
  headers: { Authorization: `Bearer ${feedbackStudent.token}` }
});
assert.equal(feedbackNotificationsResponse.status, 200, 'Student notification inbox should load');
const { notifications: feedbackNotifications } = await feedbackNotificationsResponse.json();
const feedbackNotification = feedbackNotifications.find((item) => item.type === 'feedback');
assert.ok(feedbackNotification, 'New course feedback should create a notification');
const markNotificationResponse = await fetch(`${baseUrl}/api/me/notifications/${encodeURIComponent(feedbackNotification.notificationId)}/read`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${feedbackStudent.token}` }
});
assert.equal(markNotificationResponse.status, 204, 'Student should be able to mark notifications read');
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

const lateClassName = `PortalLate${Date.now()}`;
const lateSubmissionForm = new FormData();
lateSubmissionForm.append('assignmentId', createdAssignment.assignmentId);
lateSubmissionForm.append('sourceFile', new Blob([`public class ${lateClassName} { public Object value() { return null; } }`]), `${lateClassName}.java`);
const lateUploadResponse = await fetch(`${baseUrl}/api/student/submissions`, {
  method: 'POST', headers: { Authorization: `Bearer ${academicStudent.token}` }, body: lateSubmissionForm
});
assert.equal(lateUploadResponse.status, 201, 'Late assignment submissions should be accepted until the assignment is closed');
const { submission: lateSubmission } = await lateUploadResponse.json();
assert.equal(lateSubmission.assignmentId, createdAssignment.assignmentId);
const similarClassName = `PortalLate${Date.now()}`;
const sourceStudents = parse(readFileSync(new URL('../../data/students.csv', import.meta.url), 'utf8'), {
  bom: true, columns: true, skip_empty_lines: true, trim: true
});
const studentAccounts = parse(readFileSync(new URL('../../data/users.csv', import.meta.url), 'utf8'), {
  bom: true, columns: true, skip_empty_lines: true, trim: true
}).filter((user) => user.role === 'Student');
const peerIndex = sourceStudents.findIndex((item, index) => item.CourseCode.split('|').includes(firstCourse.courseCode)
  && studentAccounts[index]?.userId !== academicStudent.user.userId);
assert.ok(peerIndex >= 0, 'An enrolled peer should be available for similarity testing');
const peer = await login(studentAccounts[peerIndex].username, studentAccounts[peerIndex].passwordHash, 'Student');
const similarSubmissionForm = new FormData();
similarSubmissionForm.append('assignmentId', createdAssignment.assignmentId);
similarSubmissionForm.append('sourceFile', new Blob([`public class ${similarClassName} { public Object value() { return null; } }`]), `${similarClassName}.java`);
const similarUploadResponse = await fetch(`${baseUrl}/api/student/submissions`, {
  method: 'POST', headers: { Authorization: `Bearer ${peer.token}` }, body: similarSubmissionForm
});
assert.equal(similarUploadResponse.status, 201, 'A distinct submission version should be accepted');
const { submission: similarSubmission } = await similarUploadResponse.json();
const similarityResponse = await fetch(`${baseUrl}/api/teacher/assignments/${createdAssignment.assignmentId}/similar-submissions`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(similarityResponse.status, 200, 'Teacher should be able to check assignment code similarity');
const { pairs: similarPairs } = await similarityResponse.json();
assert.ok(similarPairs.some((pair) => [pair.first.submissionId, pair.second.submissionId].includes(lateSubmission.submissionId)
  && [pair.first.submissionId, pair.second.submissionId].includes(similarSubmission.submissionId)), 'Highly similar code should be flagged');
const rosterResponse = await fetch(`${baseUrl}/api/teacher/assignments/${createdAssignment.assignmentId}/submissions`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(rosterResponse.status, 200, 'Teacher assignment roster should load');
const { students: assignmentRoster } = await rosterResponse.json();
assert.equal(assignmentRoster.find((item) => item.studentId === academicStudent.user.userId).late, true);
assert.ok(assignmentRoster.some((item) => !item.submitted), 'Roster should include students who have not submitted');
const submissionDetailResponse = await fetch(`${baseUrl}/api/teacher/submissions/${lateSubmission.submissionId}`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(submissionDetailResponse.status, 200, 'Teacher should open an enrolled student submission');
const { submission: submissionDetail } = await submissionDetailResponse.json();
assert.match(submissionDetail.sourceCode, new RegExp(lateClassName));
const rerunResponse = await fetch(`${baseUrl}/api/teacher/submissions/${lateSubmission.submissionId}/analyze`, {
  method: 'POST', headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(rerunResponse.status, 200, 'Teacher should rerun static analysis');

const closeAssignmentResponse = await fetch(`${baseUrl}/api/teacher/assignments/${createdAssignment.assignmentId}`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ closed: true })
});
assert.equal(closeAssignmentResponse.status, 200, 'Teacher should close an assignment');
const closedClassName = `PortalClosed${Date.now()}`;
const closedSubmissionForm = new FormData();
closedSubmissionForm.append('assignmentId', createdAssignment.assignmentId);
closedSubmissionForm.append('sourceFile', new Blob([`public class ${closedClassName} {}`]), `${closedClassName}.java`);
const closedUploadResponse = await fetch(`${baseUrl}/api/student/submissions`, {
  method: 'POST', headers: { Authorization: `Bearer ${academicStudent.token}` }, body: closedSubmissionForm
});
assert.equal(closedUploadResponse.status, 409, 'Closed assignments must reject new submissions');

const reportResponse = await fetch(`${baseUrl}/api/teacher/report`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(reportResponse.status, 200, 'Teacher report should load');
const { report } = await reportResponse.json();
assert.ok(Array.isArray(report.scoreDistribution));
assert.ok(report.assignmentRosters.some((item) => item.assignmentId === createdAssignment.assignmentId));
const csvResponse = await fetch(`${baseUrl}/api/teacher/report.csv`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(csvResponse.status, 200, 'Teacher grades CSV should export');
assert.match(await csvResponse.text(), /"Student ID","Assignment ID","File"/);
const xlsxResponse = await fetch(`${baseUrl}/api/teacher/report.xlsx`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(xlsxResponse.status, 200, 'Teacher grades XLSX should export');
assert.match(xlsxResponse.headers.get('content-type'), /spreadsheetml/);
const xlsxBytes = new Uint8Array(await xlsxResponse.arrayBuffer());
assert.equal(String.fromCharCode(...xlsxBytes.slice(0, 2)), 'PK', 'XLSX export should be a ZIP-based workbook');

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
const teacherVisibleUpload = classList.submissions.find((item) => item.submissionId === submission.submissionId);
assert.ok(teacherVisibleUpload, 'Teacher should see unassigned uploads from students in assigned courses');
assert.equal(teacherVisibleUpload.assignmentId, null, 'The regression case should be a practice upload without an assignment');
const teacherOpenSubmissionResponse = await fetch(`${baseUrl}/api/teacher/submissions/${submission.submissionId}`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(teacherOpenSubmissionResponse.status, 200, 'Teacher should be able to open an unassigned upload');
const { submission: teacherOpenedSubmission } = await teacherOpenSubmissionResponse.json();
assert.match(teacherOpenedSubmission.sourceCode, new RegExp(className));
assert.ok(classList.submissions.some((item) => item.submissionId === attachmentSubmission.submissionId), 'Teacher should see non-Java submissions from enrolled students');
const teacherAttachmentResponse = await fetch(`${baseUrl}/api/teacher/submissions/${attachmentSubmission.submissionId}/file`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(teacherAttachmentResponse.status, 200, 'Assigned teacher should be able to download the original attachment');
assert.deepEqual(new Uint8Array(await teacherAttachmentResponse.arrayBuffer()), attachmentBytes);
const unsupportedAnalysisResponse = await fetch(`${baseUrl}/api/teacher/submissions/${attachmentSubmission.submissionId}/analyze`, {
  method: 'POST', headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(unsupportedAnalysisResponse.status, 415, 'Non-Java files must not be sent to the Java rule engine');

const gradeResponse = await fetch(`${baseUrl}/api/teacher/submissions/${submission.submissionId}/grade`, {
  method: 'PATCH',
  headers: { Authorization: `Bearer ${teacher.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ totalScore: 9, deduction: 1, gradingNotes: 'Reviewed by teacher.', reason: 'Manual review against rubric.' })
});
assert.equal(gradeResponse.status, 200, 'Teacher grade update failed');
const auditResponse = await fetch(`${baseUrl}/api/teacher/submissions/${submission.submissionId}/audit`, {
  headers: { Authorization: `Bearer ${teacher.token}` }
});
assert.equal(auditResponse.status, 200, 'Teacher grade audit should be available');
const { entries: gradeAudit } = await auditResponse.json();
assert.ok(gradeAudit.some((entry) => entry.actorUserId === teacher.user.userId && entry.reason === 'Manual review against rubric.'));

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

const profileResponse = await fetch(`${baseUrl}/api/me`, { headers: { Authorization: `Bearer ${academicStudent.token}` } });
const { user: originalProfile } = await profileResponse.json();
const updateProfileResponse = await fetch(`${baseUrl}/api/me/profile`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${academicStudent.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ fullName: `${originalProfile.fullName} Test`, email: `profile-${Date.now()}@example.test` })
});
assert.equal(updateProfileResponse.status, 200, 'User should update their profile');
const restoreProfileResponse = await fetch(`${baseUrl}/api/me/profile`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${academicStudent.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ fullName: originalProfile.fullName, email: originalProfile.email })
});
assert.equal(restoreProfileResponse.status, 200, 'Profile changes should be reversible');
const changePasswordResponse = await fetch(`${baseUrl}/api/me/password`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${academicStudent.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ currentPassword: 'hash_dungdang442', newPassword: 'PortalSmokePass2026!' })
});
assert.equal(changePasswordResponse.status, 200, 'User should change password after verifying current password');
const changedPasswordLogin = await login('dungdang442', 'PortalSmokePass2026!', 'Student');
const restorePasswordResponse = await fetch(`${baseUrl}/api/me/password`, {
  method: 'PATCH', headers: { Authorization: `Bearer ${changedPasswordLogin.token}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ currentPassword: 'PortalSmokePass2026!', newPassword: 'hash_dungdang442' })
});
assert.equal(restorePasswordResponse.status, 200, 'Password should be restorable after the password test');

console.log('Smoke test passed: auth, class/enrollment/teacher management, account profile/password, notifications, course grades and feedback, assignment CRUD/closure, late submission roster, teacher source review/reanalysis, code similarity, grade audit, reports/CSV/XLSX, upload, analysis, and student grade visibility.');