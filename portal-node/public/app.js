const state = {
  token: null,
  user: null,
  view: 'dashboard',
  submissions: [],
  rules: [],
  courses: [],
  semesters: [],
  studentAssignments: [],
  selectedSemester: 'fall-2026',
  selectedCourseCode: null,
  selectedStudentId: null,
  selectedAssignmentId: null,
  assignments: [],
  assignmentCourseCode: null,
  editingAssignmentId: null,
  selectedTeacherAssignmentId: null,
  plagiarismAssignmentId: null,
  selectedTeacherSubmissionId: null,
  teacherSubmission: null,
  classes: [],
  allStudents: [],
  allTeachers: [],
  selectedClassCode: null,
  selectedClassTab: 'students',
  profile: null,
  notifications: []
};
const loginScreen = document.querySelector('#login-screen');
const portalScreen = document.querySelector('#portal-screen');
const workspace = document.querySelector('#workspace-content');
const toast = document.querySelector('#toast');

const studentNavigation = [
  ['dashboard', 'Overview'],
  ['submit', 'Submit Source Code'],
  ['grades', 'My Grades'],
  ['assignments', 'Assignments'],
  ['history', 'Submission History'],
  ['analysis', 'Analysis & Fixes']
];
const teacherNavigation = [
  ['dashboard', 'Overview'],
  ['submissions', 'Submissions'],
  ['plagiarism', 'Code similarity'],
  ['reports', 'Reports'],
  ['rules', 'Rule Engine'],
  ['edit-grade', 'Edit Grades'],
  ['feedback', 'Manual Feedback'],
  ['classes', 'Classes'],
  ['settings', 'Account settings']
];
studentNavigation.push(['settings', 'Account settings']);

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add('show');
  window.setTimeout(() => toast.classList.remove('show'), 2800);
}

async function api(url, options = {}) {
  const headers = new Headers(options.headers ?? {});
  if (state.token) headers.set('Authorization', `Bearer ${state.token}`);
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  const response = await fetch(url, { ...options, headers });
  const payload = response.status === 204 ? null : await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload?.error || `Request failed (${response.status}).`);
  return payload;
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? escapeHtml(value) : date.toLocaleDateString();
}

function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? '—' : new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function toLocalDateTime(value) {
  const date = value ? new Date(value) : new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  if (Number.isNaN(date.valueOf())) return '';
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function formatScore(value) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(1) : '—';
}

function renderNavigation() {
  const items = state.user.role.toLowerCase() === 'student' ? studentNavigation : teacherNavigation;
  document.querySelector('#main-nav').innerHTML = items.map(([view, label]) => `
    <button class="nav-button ${state.view === view ? 'active' : ''}" type="button" data-view="${view}">${label}</button>
  `).join('');
}

function setSignedIn(user, token) {
  state.user = user;
  state.token = token;
  state.view = 'dashboard';
  state.selectedCourseCode = null;
  state.selectedStudentId = null;
  state.selectedAssignmentId = null;
  loginScreen.hidden = true;
  portalScreen.hidden = false;
  document.querySelector('#account-name').textContent = user.fullName || user.username;
  document.querySelector('#account-role').textContent = user.role.toUpperCase();
  document.querySelector('#account-initial').textContent = (user.fullName || user.username).slice(0, 1).toUpperCase();
  document.querySelector('#date-label').textContent = new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date());
  renderNavigation();
  renderView();
}

function metric(label, value) {
  return `<div class="metric"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
}

function emptyState(message) {
  return `<div class="empty-state">${escapeHtml(message)}</div>`;
}

function renderAccountSettings(profile) {
  return `<div class="settings-grid">
    <form id="profile-form" class="content-form"><h2>Profile</h2>
      <label class="field-label">Full name<input name="fullName" maxlength="120" value="${escapeHtml(profile.fullName)}" required></label>
      <label class="field-label">Email<input name="email" type="email" value="${escapeHtml(profile.email)}" required></label>
      <button class="button button-primary" type="submit">Save profile</button>
    </form>
    <form id="password-form" class="content-form"><h2>Change password</h2>
      <label class="field-label">Current password<input name="currentPassword" type="password" autocomplete="current-password" required></label>
      <label class="field-label">New password<input name="newPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required></label>
      <label class="field-label">Confirm new password<input name="confirmPassword" type="password" minlength="8" autocomplete="new-password" required></label>
      <button class="button button-primary" type="submit">Update password</button>
    </form>
  </div>`;
}

function renderTeacherClasses(classes, students, teachers, selectedCode) {
  const selectedClass = classes.find((course) => course.courseCode === selectedCode) ?? classes[0];
  if (!selectedClass) return emptyState('No classes are assigned to this account.');
  state.selectedClassCode = selectedClass.courseCode;
  const classOptions = classes.map((course) => `<option value="${escapeHtml(course.courseCode)}" ${course.courseCode === selectedClass.courseCode ? 'selected' : ''}>${escapeHtml(course.courseCode)} · ${escapeHtml(course.courseName)}</option>`).join('');
  const assignedTeachers = new Set(selectedClass.teacherIds);
  const teacherChoices = teachers.map((teacher) => `<label class="check-option"><input type="checkbox" name="teacherIds" value="${escapeHtml(teacher.userId)}" ${assignedTeachers.has(teacher.userId) ? 'checked' : ''}><span>${escapeHtml(teacher.fullName)} <small>${escapeHtml(teacher.userId)}</small></span></label>`).join('');
  const enrolled = new Set(selectedClass.roster.map((student) => student.studentId));
  const studentChoices = students.filter((student) => !enrolled.has(student.studentId))
    .map((student) => `<option value="${escapeHtml(student.studentId)}">${escapeHtml(student.fullName)} · ${escapeHtml(student.studentId)}</option>`).join('');
  const rosterRows = selectedClass.roster.map((student) => `<tr><td>${escapeHtml(student.fullName)}</td><td>${escapeHtml(student.studentId)}</td><td>${escapeHtml(student.email ?? '—')}</td><td><button class="button button-danger" type="button" data-remove-student="${escapeHtml(student.studentId)}">Remove</button></td></tr>`).join('');
  const tabBar = `<div class="class-tabs" role="tablist" aria-label="Course management">
    <button class="class-tab ${state.selectedClassTab === 'students' ? 'active' : ''}" type="button" data-class-tab="students">Students <span>${selectedClass.roster.length}</span></button>
    <button class="class-tab ${state.selectedClassTab === 'assignments' ? 'active' : ''}" type="button" data-class-tab="assignments">Assignments</button>
  </div>`;
  const classContent = state.selectedClassTab === 'assignments'
    ? renderTeacherAssignments(selectedClass.courseCode)
    : `<div class="section-heading"><h2>Enrollment</h2><p>${selectedClass.roster.length} students</p></div>
      <form id="class-teachers-form" class="content-form" data-course-code="${escapeHtml(selectedClass.courseCode)}"><h3>Assigned teachers</h3><div class="teacher-choice-grid">${teacherChoices}</div><button class="button button-primary" type="submit">Save teacher assignments</button></form>
      <div class="section-heading"><h2>Students</h2><p>Roster changes are saved separately from the seed CSV</p></div>
      <form id="enroll-student-form" class="inline-form" data-course-code="${escapeHtml(selectedClass.courseCode)}"><label class="field-label">Add student<select name="studentId" required><option value="">Choose a student</option>${studentChoices}</select></label><button class="button button-primary" type="submit" ${studentChoices ? '' : 'disabled'}>Enroll</button></form>
      ${rosterRows ? `<div class="table-wrap"><table><thead><tr><th>Student</th><th>ID</th><th>Email</th><th></th></tr></thead><tbody>${rosterRows}</tbody></table></div>` : emptyState('This class has no enrolled students.')}`;
  return `<div class="content-form class-selector"><label class="field-label">Class / course<select id="class-course-select">${classOptions}</select></label></div>
    <div class="section-heading"><h2>${escapeHtml(selectedClass.courseCode)} · ${escapeHtml(selectedClass.courseName)}</h2><p>${selectedClass.teacherIds.length} assigned teachers</p></div>
    ${tabBar}${classContent}`;
}

function submissionRows(submissions, includeStudent = false) {
  if (!submissions.length) return '';
  return submissions.map((submission) => `
    <tr>
      ${includeStudent ? `<td>${escapeHtml(submission.studentName)}</td>` : ''}
      <td>${escapeHtml(submission.fileName || submission.submissionId)}</td>
      <td>${escapeHtml(submission.submissionId)}</td>
      <td><span class="pill ${submission.status === 'Needs Review' ? 'warn' : ''}">${escapeHtml(submission.status)}</span></td>
      <td class="score">${formatScore(submission.score?.totalScore)}</td>
      <td>${formatDate(submission.submittedAt)}</td>
    </tr>
  `).join('');
}

function table(headers, rows, includeStudent = false) {
  if (!rows.length) return emptyState('No records yet.');
  return `<div class="table-wrap"><table><thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join('')}</tr></thead><tbody>${submissionRows(rows, includeStudent)}</tbody></table></div>`;
}

function renderSemesterSidebar() {
  const seasons = ['TRIAL', 'SUMMER', 'SPRING', 'FALL'];
  return `<aside class="semester-panel">
    <div class="semester-panel-heading"><strong>Semesters</strong><span>${state.semesters.length}</span></div>
    ${seasons.map((season) => {
      const semesters = state.semesters.filter((semester) => semester.season === season);
      if (!semesters.length) return '';
      return `<section class="semester-group"><h2>${season}</h2>${semesters.map((semester) => `
        <button class="semester-button ${state.selectedSemester === semester.value ? 'active' : ''}" type="button" data-semester="${escapeHtml(semester.value)}">
          ${semester.year ?? 'TRIAL'}
        </button>`).join('')}</section>`;
    }).join('')}
  </aside>`;
}

function renderTeacherCoursePicker() {
  const cards = state.courses.map((course) => `
    <button class="course-card" type="button" data-course-code="${escapeHtml(course.courseCode)}">
      <span class="course-card-top"><span class="course-code-mark">${escapeHtml(course.courseCode.slice(0, 2))}</span><span class="pill">ACTIVE</span></span>
      <span class="course-card-title">${escapeHtml(course.courseName)}</span>
      <span class="course-card-subtitle">${escapeHtml(course.studentCount)} active students</span>
      <span class="course-card-footer"><span>Get started</span><span class="course-arrow" aria-hidden="true">&#8594;</span></span>
    </button>`).join('');
  return `<div class="course-layout">
    ${renderSemesterSidebar()}
    <section class="course-browser">
      <div class="course-search-row"><label class="course-search-label" for="course-search">Search courses</label><input id="course-search" type="search" placeholder="Search courses..."></div>
      ${cards ? `<div class="course-grid">${cards}</div>` : emptyState('No active courses are assigned to your account.')}
    </section>
  </div>`;
}

function renderTeacherCourseGrades(course, grades) {
  const rows = grades.map((grade) => `<tr>
    <td>${escapeHtml(grade.fullName || grade.studentName)}</td>
    <td>${escapeHtml(grade.studentId)}</td>
    <td class="score">${formatScore(grade.totalScore)}</td>
    <td>${formatScore(grade.deduction)}</td>
    <td>${escapeHtml(grade.manualFeedback || '—')}</td>
    <td><span class="pill">ACTIVE</span></td>
  </tr>`).join('');
  return `<div class="course-layout">
    ${renderSemesterSidebar()}
    <section class="course-grade-view">
      <div class="course-grade-heading">
        <div><button id="back-to-courses" class="button button-secondary" type="button">Courses</button><p class="eyebrow">${escapeHtml(state.selectedSemester.replace('-', ' ').toUpperCase())}</p><h2>${escapeHtml(course.courseName)}</h2><p>${escapeHtml(course.courseCode)} · ${grades.length} active students · Demo scores generated from GPA</p></div>
        <button id="print-report" class="button button-secondary" type="button">Print / Save PDF</button>
      </div>
      ${rows ? `<div class="table-wrap"><table><thead><tr><th>Student</th><th>Student ID</th><th>Score</th><th>Deduction</th><th>Manual feedback</th><th>Status</th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState(`No grades are available for this course in the selected semester. Demo grades are available in Fall 2026.`)}
    </section>
  </div>`;
}

function renderTeacherCourseGradeEditor(course, grades) {
  const selectedGrade = grades.find((grade) => grade.studentId === state.selectedStudentId) ?? grades[0];
  state.selectedStudentId = selectedGrade?.studentId ?? null;
  const courseOptions = state.courses.map((item) => `<option value="${escapeHtml(item.courseCode)}" ${item.courseCode === course.courseCode ? 'selected' : ''}>${escapeHtml(item.courseCode)}</option>`).join('');
  const semesterOptions = state.semesters.map((semester) => {
    const label = semester.year ? `${semester.season} ${semester.year}` : 'TRIAL';
    return `<option value="${escapeHtml(semester.value)}" ${semester.value === state.selectedSemester ? 'selected' : ''}>${escapeHtml(label)}</option>`;
  }).join('');
  const contextFields = `<div class="field-row"><label class="field-label">Course<select id="course-grade-course" name="courseCode">${courseOptions}</select></label><label class="field-label">Semester<select id="course-grade-semester" name="semester">${semesterOptions}</select></label></div>`;
  if (!selectedGrade) {
    return `<div class="section-heading"><h2>Edit course grades</h2></div><div class="content-form">${contextFields}</div>${emptyState('No grades are available for this course in the selected semester. Demo grades are available in Fall 2026.')}`;
  }
  const studentOptions = grades.map((grade) => `<option value="${escapeHtml(grade.studentId)}" data-score="${escapeHtml(grade.totalScore)}" data-deduction="${escapeHtml(grade.deduction)}" data-notes="${escapeHtml(grade.gradingNotes)}" ${grade.studentId === selectedGrade.studentId ? 'selected' : ''}>${escapeHtml(grade.fullName || grade.studentName)} · ${escapeHtml(grade.studentId)}</option>`).join('');
  return `<div class="section-heading"><h2>Edit course grades</h2><p>Demo score generated from GPA; current value is prefilled</p></div>
    <form id="course-grade-form" class="content-form">
      ${contextFields}
      <label class="field-label">Student<select id="course-grade-student" name="studentId">${studentOptions}</select></label>
      <div class="field-row"><label class="field-label">Score<input name="totalScore" type="number" min="0" max="10" step="0.1" value="${escapeHtml(selectedGrade.totalScore)}" required></label><label class="field-label">Deduction<input name="deduction" type="number" min="0" max="10" step="0.1" value="${escapeHtml(selectedGrade.deduction)}" required></label></div>
      <label class="field-label">Grading notes<textarea name="gradingNotes" maxlength="2000">${escapeHtml(selectedGrade.gradingNotes)}</textarea></label>
      <button class="button button-primary" type="submit">Save grade</button>
    </form>`;
}

function renderTeacherCourseFeedback(course, grades) {
  const selectedGrade = grades.find((grade) => grade.studentId === state.selectedStudentId) ?? grades[0];
  state.selectedStudentId = selectedGrade?.studentId ?? null;
  const courseOptions = state.courses.map((item) => `<option value="${escapeHtml(item.courseCode)}" ${item.courseCode === course.courseCode ? 'selected' : ''}>${escapeHtml(item.courseCode)}</option>`).join('');
  const semesterOptions = state.semesters.map((semester) => {
    const label = semester.year ? `${semester.season} ${semester.year}` : 'TRIAL';
    return `<option value="${escapeHtml(semester.value)}" ${semester.value === state.selectedSemester ? 'selected' : ''}>${escapeHtml(label)}</option>`;
  }).join('');
  const contextFields = `<div class="field-row"><label class="field-label">Course<select id="feedback-course" name="courseCode">${courseOptions}</select></label><label class="field-label">Semester<select id="feedback-semester" name="semester">${semesterOptions}</select></label></div>`;
  if (!selectedGrade) {
    return `<div class="section-heading"><h2>Manual feedback</h2></div><div class="content-form">${contextFields}</div>${emptyState('No students are enrolled in this course for the selected semester.')}`;
  }
  const studentOptions = grades.map((grade) => `<option value="${escapeHtml(grade.studentId)}" ${grade.studentId === selectedGrade.studentId ? 'selected' : ''}>${escapeHtml(grade.fullName || grade.studentName)} · ${escapeHtml(grade.studentId)}</option>`).join('');
  return `<div class="section-heading"><h2>Manual feedback</h2><p>Feedback is saved with this course grade</p></div>
    <form id="course-feedback-form" class="content-form">
      ${contextFields}
      <label class="field-label">Student<select id="feedback-student" name="studentId">${studentOptions}</select></label>
      <label class="field-label">Feedback<textarea name="manualFeedback" maxlength="4000" placeholder="Write feedback for this student...">${escapeHtml(selectedGrade.manualFeedback || '')}</textarea></label>
      <button class="button button-primary" type="submit">Save feedback</button>
    </form>`;
}

function renderTeacherAssignmentRoster(assignment, students) {
  const rows = students.map((student) => `<tr>
    <td>${escapeHtml(student.fullName)}</td><td>${escapeHtml(student.studentId)}</td>
    <td>${student.submitted ? '<span class="pill">Submitted</span>' : '<span class="pill warn">Not submitted</span>'}${student.late ? ' <span class="pill warn">Late</span>' : ''}</td>
    <td>${formatDateTime(student.submittedAt)}</td>
    <td>${student.submission ? formatScore(student.submission.score?.totalScore) : '—'}</td>
    <td>${student.submission ? `<button class="button button-secondary" type="button" data-review-submission="${escapeHtml(student.submission.submissionId)}">Open</button>` : '—'}</td>
  </tr>`).join('');
  const submittedCount = students.filter((student) => student.submitted).length;
  return `<div class="course-grade-heading"><div><button class="button button-secondary" type="button" data-back-to-submission-list>Assignments</button><p class="eyebrow">${escapeHtml(assignment.courseCode)}</p><h2>${escapeHtml(assignment.title)}</h2><p>Due ${formatDateTime(assignment.dueAt)} · ${submittedCount}/${students.length} submitted</p></div></div>
    ${rows ? `<div class="table-wrap"><table><thead><tr><th>Student</th><th>ID</th><th>Status</th><th>Submitted</th><th>Score</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('No students are enrolled in this course.')}`;
}

function renderTeacherSubmissionList(submissions) {
  const rows = [...submissions]
    .sort((first, second) => new Date(second.submittedAt) - new Date(first.submittedAt))
    .map((submission) => `<tr>
      <td>${escapeHtml(submission.studentName)}</td>
      <td>${escapeHtml(submission.fileName || submission.submissionId)}</td>
      <td>${submission.assignmentId ? escapeHtml(state.assignments.find((item) => item.assignmentId === submission.assignmentId)?.title ?? 'Assignment') : 'Practice upload'}</td>
      <td><span class="pill ${submission.status === 'Needs Review' ? 'warn' : ''}">${escapeHtml(submission.status)}</span></td>
      <td class="score">${formatScore(submission.score?.totalScore)}</td>
      <td>${formatDateTime(submission.submittedAt)}</td>
      <td><button class="button button-secondary" type="button" data-review-submission="${escapeHtml(submission.submissionId)}">Open</button></td>
    </tr>`).join('');
  return `<div class="section-heading"><h2>All submissions</h2><p>${submissions.length} from students in your courses</p></div>
    ${rows ? `<div class="table-wrap"><table><thead><tr><th>Student</th><th>File</th><th>Assignment</th><th>Status</th><th>Score</th><th>Submitted</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('No students in your courses have submitted work yet.')}`;
}

function renderTeacherSubmissionDetail(submission, auditEntries) {
  const sourceLines = String(submission.sourceCode ?? '').split('\n');
  const findings = submission.analysis?.violations ?? [];
  const findingRows = findings.map((finding) => {
    const lineNumber = Number(finding.lineNumber);
    return `<article class="finding"><strong>${escapeHtml(finding.ruleCode || finding.ruleName)}</strong><span>Line ${escapeHtml(lineNumber)}${finding.severity ? ` · ${escapeHtml(finding.severity)}` : ''}</span><pre>${escapeHtml(sourceLines[lineNumber - 1] ?? '')}</pre><p>${escapeHtml(finding.suggestedFix)}</p></article>`;
  }).join('');
  return `<div class="section-heading"><div><p class="eyebrow">${escapeHtml(submission.studentName)} · ${escapeHtml(submission.submissionId)}</p><h2>${escapeHtml(submission.fileName)}</h2></div><button class="button button-secondary" type="button" data-back-to-submission-roster>Back to roster</button></div>
    <p class="assignment-course-label">Submitted ${formatDateTime(submission.submittedAt)} · Score ${formatScore(submission.score?.totalScore)} · ${escapeHtml(submission.status)}</p>
    <div class="submission-toolbar"><button class="button button-secondary" type="button" data-download-submission="${escapeHtml(submission.submissionId)}" data-owner="teacher">Download original</button>${submission.fileName.toLowerCase().endsWith('.java') ? `<button class="button button-primary" type="button" data-analyze-submission="${escapeHtml(submission.submissionId)}">Run analysis</button>` : ''}</div>
    <div class="analysis-list"><section class="analysis-entry"><div class="section-heading"><h2>Rule violations</h2><span class="pill">${findings.length} findings</span></div>${findingRows || emptyState('No configured rule violations found.')}</section></div>
    ${submission.sourceCode !== null && submission.sourceCode !== undefined ? `<pre class="source-code">${escapeHtml(submission.sourceCode)}</pre>` : `<p class="notice">This file type is stored as an original attachment. Download it to view the contents.</p>`}
    <div class="teacher-review-grid">
      ${submission.fileName.toLowerCase().endsWith('.java') ? `<form id="submission-grade-form" class="content-form" data-submission-id="${escapeHtml(submission.submissionId)}">
        <h3>Edit grade</h3><label class="field-label">Score<input name="totalScore" type="number" min="0" max="10" step="0.1" value="${escapeHtml(submission.score?.totalScore ?? 0)}" required></label>
        <label class="field-label">Deduction<input name="deduction" type="number" min="0" max="10" step="0.1" value="${escapeHtml(submission.score?.deduction ?? 0)}" required></label>
        <label class="field-label">Reason for change<input name="reason" maxlength="500" required></label>
        <label class="field-label">Grading notes<textarea name="gradingNotes" maxlength="2000">${escapeHtml(submission.score?.gradingNotes ?? '')}</textarea></label>
        <button class="button button-primary" type="submit">Save grade</button>
      </form>` : ''}
      <form id="submission-feedback-form" class="content-form" data-submission-id="${escapeHtml(submission.submissionId)}">
        <h3>Manual feedback</h3><label class="field-label">Feedback<textarea name="feedback" maxlength="4000" required>${escapeHtml(submission.manualFeedback ?? '')}</textarea></label>
        <button class="button button-primary" type="submit">Save feedback</button>
      </form>
    </div>
    <div class="section-heading"><h2>Grade history</h2><p>${auditEntries.length} manual changes</p></div>
    ${auditEntries.length ? `<div class="audit-list">${auditEntries.map((entry) => `<article><strong>${escapeHtml(entry.actorName)}</strong><time>${formatDateTime(entry.changedAt)}</time><p>${escapeHtml(entry.previous?.totalScore ?? '—')} → ${escapeHtml(entry.next.totalScore)} · ${escapeHtml(entry.reason || 'No reason entered')}</p></article>`).join('')}</div>` : emptyState('No grade changes have been recorded.')}`;
}

  function renderTeacherPlagiarism(assignments, selectedAssignmentId, pairs) {
    const options = assignments.map((assignment) => `<option value="${escapeHtml(assignment.assignmentId)}" ${assignment.assignmentId === selectedAssignmentId ? 'selected' : ''}>${escapeHtml(assignment.courseCode)} · ${escapeHtml(assignment.title)}</option>`).join('');
    const rows = pairs.map((pair) => `<tr><td>${escapeHtml(pair.first.studentName)}<small class="assignment-description">${escapeHtml(pair.first.fileName)}</small></td><td>${escapeHtml(pair.second.studentName)}<small class="assignment-description">${escapeHtml(pair.second.fileName)}</small></td><td class="score">${pair.similarity}%</td><td><button class="button button-secondary" type="button" data-review-submission="${escapeHtml(pair.first.submissionId)}">Review first</button> <button class="button button-secondary" type="button" data-review-submission="${escapeHtml(pair.second.submissionId)}">Review second</button></td></tr>`).join('');
    return `<div class="content-form"><label class="field-label" for="plagiarism-assignment">Assignment</label><select id="plagiarism-assignment">${options}</select></div>
    <div class="section-heading"><h2>Potentially similar submissions</h2><p>Token-shingle similarity is a review signal, not a finding of misconduct.</p></div>
    ${rows ? `<div class="table-wrap"><table><thead><tr><th>Student A</th><th>Student B</th><th>Similarity</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('No submission pairs crossed the similarity threshold for this assignment.')}`;
  }

function renderTeacherReport(report) {
  const assignmentRoster = report.assignmentRosters ?? [];
  const totalEnrolled = assignmentRoster.reduce((sum, item) => sum + item.enrolledCount, 0);
  const totalSubmitted = assignmentRoster.reduce((sum, item) => sum + item.submittedCount, 0);
  const submissionRate = totalEnrolled ? `${(totalSubmitted / totalEnrolled * 100).toFixed(1)}%` : '—';
  const maxBucket = Math.max(1, ...(report.scoreDistribution ?? []).map((item) => item.count));
  const buckets = (report.scoreDistribution ?? []).map((item) => `<div class="distribution-row"><span>${escapeHtml(item.range)}</span><div class="distribution-track"><span style="width:${item.count / maxBucket * 100}%"></span></div><strong>${item.count}</strong></div>`).join('');
  const commonRules = (report.mostCommonViolations ?? []).map((item) => `<tr><td>${escapeHtml(item.ruleCode)}</td><td>${item.count}</td></tr>`).join('');
  const assignments = assignmentRoster.map((item) => `<tr><td>${escapeHtml(item.courseCode)}</td><td>${escapeHtml(item.title)}</td><td>${item.submittedCount}/${item.enrolledCount}</td><td>${item.submissionRate}%</td></tr>`).join('');
  return `<div class="report-actions"><button id="export-grades-csv" class="button button-secondary" type="button">Export grades CSV</button><button id="export-grades-xlsx" class="button button-primary" type="button">Export Excel</button></div>
    <div class="metric-grid">${metric('Class average', report.classAverage === null ? '—' : formatScore(report.classAverage))}${metric('Submissions', report.submissionCount)}${metric('Graded', report.gradedSubmissionCount)}${metric('Submission rate', submissionRate)}</div>
    <div class="report-grid"><section><div class="section-heading"><h2>Score distribution</h2></div><div class="distribution-list">${buckets || emptyState('No scores yet.')}</div></section>
      <section><div class="section-heading"><h2>Most common violations</h2></div>${commonRules ? `<div class="table-wrap"><table><thead><tr><th>Rule</th><th>Count</th></tr></thead><tbody>${commonRules}</tbody></table></div>` : emptyState('No rule violations recorded.')}</section></div>
    <div class="section-heading"><h2>Assignment submission rates</h2></div>${assignments ? `<div class="table-wrap"><table><thead><tr><th>Course</th><th>Assignment</th><th>Submitted</th><th>Rate</th></tr></thead><tbody>${assignments}</tbody></table></div>` : emptyState('No assignment data yet.')}`;
}

function renderTeacherAssignments(courseCode = null) {
  const selectedAssignment = state.assignments.find((assignment) => assignment.assignmentId === state.editingAssignmentId);
  if (state.editingAssignmentId && !selectedAssignment) state.editingAssignmentId = null;
  const defaultCourse = courseCode ?? (state.courses.some((course) => course.courseCode === state.assignmentCourseCode)
    ? state.assignmentCourseCode
    : selectedAssignment?.courseCode ?? state.courses[0]?.courseCode ?? '');
  state.assignmentCourseCode = defaultCourse;
  const courseOptions = courseCode
    ? `<input type="hidden" name="courseCode" value="${escapeHtml(courseCode)}"><p class="course-fixed-value">${escapeHtml(courseCode)}</p>`
    : `<select name="courseCode" required>${state.courses.map((course) => `<option value="${escapeHtml(course.courseCode)}" ${course.courseCode === (selectedAssignment?.courseCode ?? defaultCourse) ? 'selected' : ''}>${escapeHtml(course.courseCode)}</option>`).join('')}</select>`;
  const assignmentRows = [...state.assignments].filter((assignment) => !courseCode || assignment.courseCode === courseCode).sort((first, second) => new Date(first.dueAt) - new Date(second.dueAt)).map((assignment) => `
    <tr>
      <td>${escapeHtml(assignment.courseCode)}</td>
      <td><strong>${escapeHtml(assignment.title)}</strong><small class="assignment-description">${escapeHtml(assignment.description || '')}</small></td>
      <td>${formatDateTime(assignment.dueAt)}</td>
      <td><span class="pill ${assignment.status === 'Expired' ? 'warn' : ''}">${escapeHtml(assignment.status)}</span></td>
      <td><div class="assignment-actions">
        <button class="button button-secondary" type="button" data-review-assignment="${escapeHtml(assignment.assignmentId)}">Submissions</button>
        <button class="button button-secondary" type="button" data-toggle-assignment="${escapeHtml(assignment.assignmentId)}" data-closed="${Boolean(assignment.closed)}">${assignment.closed ? 'Reopen' : 'Close'}</button>
        <button class="button button-secondary" type="button" data-edit-assignment="${escapeHtml(assignment.assignmentId)}">Edit</button>
        <button class="button button-danger" type="button" data-delete-assignment="${escapeHtml(assignment.assignmentId)}">Delete</button>
      </div></td>
    </tr>`).join('');
  const formTitle = selectedAssignment ? 'Edit assignment' : 'Create assignment';
  return `<div class="section-heading"><h2>${courseCode ? `${escapeHtml(courseCode)} assignments` : 'Assignments'}</h2><p>Create homework and manage deadlines</p></div>
    <form id="assignment-form" class="content-form">
      <input type="hidden" name="assignmentId" value="${escapeHtml(selectedAssignment?.assignmentId ?? '')}">
      <label class="field-label">Course${courseOptions}</label>
      <label class="field-label">Title<input name="title" maxlength="120" value="${escapeHtml(selectedAssignment?.title ?? '')}" required></label>
      <label class="field-label">Description<textarea name="description" maxlength="4000">${escapeHtml(selectedAssignment?.description ?? '')}</textarea></label>
      <label class="field-label">Due date and time<input name="dueAt" type="datetime-local" value="${toLocalDateTime(selectedAssignment?.dueAt)}" required></label>
      <button class="button button-primary" type="submit">${formTitle}</button>
      ${selectedAssignment ? '<button id="cancel-assignment-edit" class="button button-secondary" type="button">Cancel</button>' : ''}
    </form>
    <div class="section-heading"><h2>Assignment list</h2><p>${state.assignments.filter((assignment) => !courseCode || assignment.courseCode === courseCode).length} assignments</p></div>
    ${assignmentRows ? `<div class="table-wrap"><table class="assignment-table"><thead><tr><th>Course</th><th>Assignment</th><th>Due</th><th>Status</th><th>Actions</th></tr></thead><tbody>${assignmentRows}</tbody></table></div>` : emptyState('No assignments yet.')}`;
}

function studentCourseGradeTable(courseGrades) {
  if (!courseGrades.length) return emptyState('No course grades are available yet.');
  const rows = courseGrades.map((grade) => `<tr>
    <td>${escapeHtml(grade.courseCode)}</td>
    <td class="score">${formatScore(grade.totalScore)}</td>
    <td>${formatScore(grade.deduction)}</td>
    <td>${escapeHtml(grade.manualFeedback || '—')}</td>
  </tr>`).join('');
  return `<div class="table-wrap"><table><thead><tr><th>Course</th><th>Score</th><th>Deduction</th><th>Teacher feedback</th></tr></thead><tbody>${rows}</tbody></table></div>`;
}

function renderStudentNotifications(assignments, notifications) {
  const unreadAssignments = assignments.filter((assignment) => !assignment.isRead);
  const assignmentItems = unreadAssignments.map((assignment) => `
    <article class="student-notification">
      <div><strong>New assignment in ${escapeHtml(assignment.courseCode)}: ${escapeHtml(assignment.title)}</strong><p>Due ${formatDateTime(assignment.dueAt)} · ${escapeHtml(assignment.status)}</p></div>
      <button class="button button-secondary" type="button" data-view-student-assignment="${escapeHtml(assignment.assignmentId)}">View assignment</button>
    </article>`).join('');
  return `<section class="student-notifications">
    <div class="student-notifications-heading"><h2>Notifications</h2><span>${unreadAssignments.length} new</span></div>
    ${notifications.filter((item) => !item.isRead).map((item) => `<article class="student-notification"><div><strong>${escapeHtml(item.title)}</strong><p>${escapeHtml(item.message)}</p></div><button class="button button-secondary" type="button" data-read-notification="${escapeHtml(item.notificationId)}">Mark read</button></article>`).join('')}
    ${assignmentItems || emptyState('No new assignments.')}
    <button class="text-action" type="button" data-view="assignments">View all assignments</button>
  </section>`;
}

function renderStudentCoursePicker() {
  const cards = state.courses.map((course) => `
    <button class="course-card" type="button" data-course-code="${escapeHtml(course.courseCode)}">
      <span class="course-card-top"><span class="course-code-mark">${escapeHtml(course.courseCode.slice(0, 2))}</span><span class="pill">ENROLLED</span></span>
      <span class="course-card-title">${escapeHtml(course.courseName)}</span>
      <span class="course-card-subtitle">Your course grades</span>
      <span class="course-card-footer"><span>View grades</span><span class="course-arrow" aria-hidden="true">&#8594;</span></span>
    </button>`).join('');
  return `<div class="course-layout">
    ${renderSemesterSidebar()}
    <section class="course-browser">
      <div class="course-search-row"><label class="course-search-label" for="course-search">Search courses</label><input id="course-search" type="search" placeholder="Search courses..."></div>
      ${cards ? `<div class="course-grid">${cards}</div>` : emptyState('No enrolled courses were found.')}
    </section>
  </div>`;
}

function renderStudentCourseGrades(course, courseGrades) {
  const grades = courseGrades.filter((grade) => grade.courseCode === course.courseCode
    && grade.semester === state.selectedSemester);
  return `<div class="course-layout">
    ${renderSemesterSidebar()}
    <section class="course-grade-view">
      <div class="course-grade-heading">
        <div><button id="back-to-courses" class="button button-secondary" type="button">Courses</button><p class="eyebrow">${escapeHtml(state.selectedSemester.replace('-', ' ').toUpperCase())}</p><h2>${escapeHtml(course.courseName)}</h2><p>${escapeHtml(course.courseCode)} · Your grades</p></div>
      </div>
      ${grades.length ? studentCourseGradeTable(grades) : emptyState('No grades are available for this course in the selected semester. Demo grades are available in Fall 2026.')}
    </section>
  </div>`;
}

function renderStudentAssignments(assignments) {
  const selectedAssignment = assignments.find((assignment) => assignment.assignmentId === state.selectedAssignmentId);
  if (state.selectedAssignmentId && !selectedAssignment) state.selectedAssignmentId = null;
  if (selectedAssignment) {
    return `<section class="student-assignment-detail">
      <button id="back-to-student-assignments" class="button button-secondary" type="button">All assignments</button>
      <div class="section-heading"><h2>${escapeHtml(selectedAssignment.title)}</h2><span class="pill ${selectedAssignment.status === 'Expired' ? 'warn' : ''}">${escapeHtml(selectedAssignment.status)}</span></div>
      <p class="assignment-course-label">${escapeHtml(selectedAssignment.courseCode)} · Due ${formatDateTime(selectedAssignment.dueAt)}</p>
      <div class="student-assignment-description">${escapeHtml(selectedAssignment.description || 'No additional instructions.')}</div>
    </section>`;
  }
  const rows = [...assignments].sort((first, second) => new Date(first.dueAt) - new Date(second.dueAt)).map((assignment) => `
    <tr>
      <td>${escapeHtml(assignment.courseCode)}</td>
      <td><strong>${escapeHtml(assignment.title)}</strong>${assignment.isRead ? '' : '<span class="pill assignment-new">NEW</span>'}</td>
      <td>${formatDateTime(assignment.dueAt)}</td>
      <td><span class="pill ${assignment.status === 'Expired' ? 'warn' : ''}">${escapeHtml(assignment.status)}</span></td>
      <td><button class="button button-secondary" type="button" data-view-student-assignment="${escapeHtml(assignment.assignmentId)}">View</button></td>
    </tr>`).join('');
  return `<div class="section-heading"><h2>Assignments</h2><p>${assignments.length} assigned to your courses</p></div>
    ${rows ? `<div class="table-wrap"><table class="student-assignment-table"><thead><tr><th>Course</th><th>Assignment</th><th>Due</th><th>Status</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('No assignments have been assigned to your courses yet.')}`;
}

async function renderStudentView() {
  if (state.view === 'dashboard') {
    const [coursePayload, gradePayload, assignmentPayload, notificationPayload] = await Promise.all([
      api('/api/student/courses'), api('/api/student/course-grades'), api('/api/student/assignments'), api('/api/me/notifications')
    ]);
    state.courses = coursePayload.courses;
    state.semesters = coursePayload.semesters;
    state.studentAssignments = assignmentPayload.assignments;
    state.notifications = notificationPayload.notifications;
    if (!state.semesters.some((semester) => semester.value === state.selectedSemester)) {
      state.selectedSemester = state.semesters[0]?.value ?? '';
    }
    if (!state.courses.some((course) => course.courseCode === state.selectedCourseCode)) {
      state.selectedCourseCode = null;
    }
    const selectedCourse = state.courses.find((course) => course.courseCode === state.selectedCourseCode);
    const notifications = renderStudentNotifications(state.studentAssignments, state.notifications);
    return `${notifications}${selectedCourse
      ? renderStudentCourseGrades(selectedCourse, gradePayload.courseGrades)
      : renderStudentCoursePicker()}`;
  }

  if (state.view === 'assignments') {
    const payload = await api('/api/student/assignments');
    state.studentAssignments = payload.assignments;
    return renderStudentAssignments(state.studentAssignments);
  }

  const [submissionPayload, gradePayload, courseGradePayload] = await Promise.all([
    api('/api/student/submissions'), api('/api/student/grades'), api('/api/student/course-grades')
  ]);
  state.submissions = submissionPayload.submissions;
  const grades = gradePayload.grades;
  const courseGrades = courseGradePayload.courseGrades;

  if (state.view === 'submit') {
    state.studentAssignments = (await api('/api/student/assignments')).assignments;
    const assignmentOptions = state.studentAssignments.map((assignment) => `<option value="${escapeHtml(assignment.assignmentId)}" ${assignment.status === 'Closed' ? 'disabled' : ''}>${escapeHtml(assignment.courseCode)} · ${escapeHtml(assignment.title)} · ${escapeHtml(assignment.status)}</option>`).join('');
    return `<div class="section-heading"><h2>Submit a file</h2><p>Any file type · 512 KB maximum</p></div>
      <form id="upload-form" class="content-form">
        <label class="field-label" for="submission-assignment">Assignment</label>
        <select id="submission-assignment" name="assignmentId"><option value="">Unassigned practice upload</option>${assignmentOptions}</select>
        <label class="field-label" for="source-file">Source file</label>
        <input id="source-file" name="sourceFile" type="file" required>
        <p class="login-note">Java files are checked by the rule engine. Other file types are stored as submitted and can be downloaded by your teacher. Maximum size: 512 KB.</p>
        <button class="button button-primary" type="submit">Submit file</button>
      </form>`;
  }

  if (state.view === 'grades') {
    const rows = grades.map((grade) => `<tr><td>${escapeHtml(grade.fileName)}</td><td>${escapeHtml(grade.submissionId)}</td><td class="score">${formatScore(grade.totalScore)}</td><td>${formatScore(grade.deduction)}</td><td>${escapeHtml(grade.gradingNotes || '')}</td><td>${grade.manuallyEdited ? 'Teacher review' : 'Rule engine'}</td></tr>`).join('');
    return `${courseGrades.length ? `<div class="section-heading"><h2>Course grades</h2><p>${courseGrades.length} enrolled courses</p></div>${studentCourseGradeTable(courseGrades)}` : ''}
      ${rows ? `<div class="section-heading"><h2>Submission grades</h2><p>Scores for your submitted source code</p></div><div class="table-wrap"><table><thead><tr><th>File</th><th>Submission ID</th><th>Score</th><th>Deduction</th><th>Notes</th><th>Source</th></tr></thead><tbody>${rows}</tbody></table></div>` : (!courseGrades.length ? emptyState('No grades are available yet.') : '')}`;
  }

  if (state.view === 'history') {
    const rows = state.submissions.map((submission) => `<tr><td>${escapeHtml(submission.fileName)}</td><td>${escapeHtml(submission.submissionId)}</td><td>${escapeHtml(submission.status)}</td><td>${formatScore(submission.score?.totalScore)}</td><td>${formatDate(submission.submittedAt)}</td><td><button class="button button-secondary" type="button" data-download-submission="${escapeHtml(submission.submissionId)}" data-owner="student">Download</button></td></tr>`).join('');
    return `<div class="section-heading"><h2>Submission history</h2><p>${state.submissions.length} records</p></div>${rows ? `<div class="table-wrap"><table><thead><tr><th>File</th><th>Submission ID</th><th>Status</th><th>Score</th><th>Submitted</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('No submissions yet.')}`;
  }

  const analyses = state.submissions.filter((submission) => submission.analysis);
  const attachments = state.submissions.filter((submission) => !submission.analysis);
  if (!analyses.length && !attachments.length) return `<div class="section-heading"><h2>Analysis &amp; fixes</h2></div>${emptyState('Analysis results will appear after you submit Java source code.')}`;
  if (!analyses.length) {
    return `<div class="section-heading"><h2>Submitted files</h2><p>Java files receive automatic analysis. Other file types are stored as attachments.</p></div>
      <div class="attachment-list">${attachments.map((submission) => `<article><div><strong>${escapeHtml(submission.fileName)}</strong><p>Submitted ${formatDateTime(submission.submittedAt)} · ${escapeHtml(submission.status)}</p></div><button class="button button-secondary" type="button" data-download-submission="${escapeHtml(submission.submissionId)}" data-owner="student">Download</button></article>`).join('')}</div>`;
  }
  return `<div class="section-heading"><h2>Analysis &amp; fixes</h2><p>Static analysis results for your submissions</p></div>
    ${attachments.length ? `<div class="section-heading"><h2>Other submitted files</h2></div><div class="attachment-list">${attachments.map((submission) => `<article><div><strong>${escapeHtml(submission.fileName)}</strong><p>Submitted ${formatDateTime(submission.submittedAt)} · ${escapeHtml(submission.status)}</p></div><button class="button button-secondary" type="button" data-download-submission="${escapeHtml(submission.submissionId)}" data-owner="student">Download</button></article>`).join('')}</div>` : ''}
    <div class="analysis-list">${analyses.map((submission) => `
      <section class="analysis-entry">
        <div class="section-heading"><h2>${escapeHtml(submission.fileName)}</h2><span class="pill ${submission.analysis.syntaxValid ? '' : 'warn'}">${submission.analysis.syntaxValid ? 'Syntax valid' : 'Needs review'}</span></div>
        <p>${escapeHtml(submission.analysis.aiFeedback || 'No feedback available.')}</p>
        ${submission.manualFeedback ? `<p><strong>Teacher feedback:</strong> ${escapeHtml(submission.manualFeedback)}</p>` : ''}
        ${submission.analysis.providerWarning ? `<p class="login-note">External AI feedback unavailable: ${escapeHtml(submission.analysis.providerWarning)}</p>` : ''}
        ${submission.analysis.violations?.length ? `<div class="finding-list">${submission.analysis.violations.map((finding) => `<div class="finding"><strong>${escapeHtml(finding.ruleCode || finding.ruleName)}</strong> Line ${escapeHtml(finding.lineNumber)}: ${escapeHtml(finding.suggestedFix)}</div>`).join('')}</div>` : emptyState('No configured rule violations found.')}
        ${submission.sourceCode ? `<button class="button button-secondary run-sandbox" type="button" data-submission-id="${escapeHtml(submission.submissionId)}">Run in sandbox</button>` : ''}
      </section>`).join('')}</div>`;
}

async function renderTeacherView() {
    if (state.view === 'classes') {
      const [payload, assignmentPayload] = await Promise.all([
        api('/api/teacher/classes'), api('/api/teacher/assignments')
      ]);
      state.classes = payload.classes;
      state.allStudents = payload.students;
      state.allTeachers = payload.teachers;
      state.assignments = assignmentPayload.assignments;
      return renderTeacherClasses(state.classes, state.allStudents, state.allTeachers, state.selectedClassCode);
    }

  if (state.view === 'submissions') {
    const [assignmentPayload, submissionPayload] = await Promise.all([
      api('/api/teacher/assignments'), api('/api/teacher/submissions')
    ]);
    state.assignments = assignmentPayload.assignments;
    if (state.selectedTeacherSubmissionId) {
      const payload = await api(`/api/teacher/submissions/${encodeURIComponent(state.selectedTeacherSubmissionId)}`);
      state.teacherSubmission = payload.submission;
      const auditPayload = await api(`/api/teacher/submissions/${encodeURIComponent(state.selectedTeacherSubmissionId)}/audit`);
      return renderTeacherSubmissionDetail(state.teacherSubmission, auditPayload.entries);
    }
    if (state.selectedTeacherAssignmentId) {
      const payload = await api(`/api/teacher/assignments/${encodeURIComponent(state.selectedTeacherAssignmentId)}/submissions`);
      return renderTeacherAssignmentRoster(payload.assignment, payload.students);
    }
    const rows = state.assignments.map((assignment) => `<tr><td>${escapeHtml(assignment.courseCode)}</td><td>${escapeHtml(assignment.title)}</td><td>${formatDateTime(assignment.dueAt)}</td><td>${escapeHtml(assignment.status)}</td><td><button class="button button-secondary" type="button" data-review-assignment="${escapeHtml(assignment.assignmentId)}">View roster</button></td></tr>`).join('');
    return `${renderTeacherSubmissionList(submissionPayload.submissions)}
      <div class="section-heading"><h2>Assignments</h2><p>${state.assignments.length} assignments</p></div>
      ${rows ? `<div class="table-wrap"><table><thead><tr><th>Course</th><th>Assignment</th><th>Deadline</th><th>Status</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('Create an assignment to track submissions by student.')}`;
  }

  if (state.view === 'plagiarism') {
    const assignmentPayload = await api('/api/teacher/assignments');
    state.assignments = assignmentPayload.assignments;
    if (!state.assignments.some((assignment) => assignment.assignmentId === state.plagiarismAssignmentId)) {
      state.plagiarismAssignmentId = state.assignments[0]?.assignmentId ?? null;
    }
    if (!state.plagiarismAssignmentId) return emptyState('Create an assignment and collect submissions to compare code.');
    const payload = await api(`/api/teacher/assignments/${encodeURIComponent(state.plagiarismAssignmentId)}/similar-submissions`);
    return renderTeacherPlagiarism(state.assignments, state.plagiarismAssignmentId, payload.pairs);
  }

  if (state.view === 'reports') {
    const payload = await api('/api/teacher/report');
    return renderTeacherReport(payload.report);
  }

  if (state.view === 'dashboard' || state.view === 'edit-grade' || state.view === 'feedback') {
    const coursePayload = await api('/api/teacher/courses');
    state.courses = coursePayload.courses;
    state.semesters = coursePayload.semesters;
    if (!state.semesters.some((semester) => semester.value === state.selectedSemester)) {
      state.selectedSemester = state.semesters[0]?.value ?? '';
    }
    let selectedCourse = state.courses.find((course) => course.courseCode === state.selectedCourseCode);
    if (!selectedCourse && state.view !== 'dashboard') {
      selectedCourse = state.courses[0];
      state.selectedCourseCode = selectedCourse?.courseCode ?? null;
    }
    if (!selectedCourse) return state.view === 'dashboard' ? renderTeacherCoursePicker() : emptyState('No active courses are assigned to your account.');
    const query = new URLSearchParams({ courseCode: selectedCourse.courseCode, semester: state.selectedSemester });
    const gradePayload = await api(`/api/teacher/course-grades?${query}`);
    if (state.view === 'edit-grade') return renderTeacherCourseGradeEditor(selectedCourse, gradePayload.grades);
    if (state.view === 'feedback') return renderTeacherCourseFeedback(selectedCourse, gradePayload.grades);
    return renderTeacherCourseGrades(selectedCourse, gradePayload.grades);
  }

  if (state.view === 'assignments') {
    const [coursePayload, assignmentPayload] = await Promise.all([
      api('/api/teacher/courses'), api('/api/teacher/assignments')
    ]);
    state.courses = coursePayload.courses;
    state.semesters = coursePayload.semesters;
    state.assignments = assignmentPayload.assignments;
    return renderTeacherAssignments();
  }

  if (state.view === 'rules') {
    const payload = await api('/api/teacher/rules');
    state.rules = payload.rules;
    return `<div class="section-heading"><h2>Rule engine configuration</h2><p>Penalty range: 0 to 10 points</p></div>
      <div class="rule-list">${state.rules.map((rule) => `<form class="rule-row" data-rule-code="${escapeHtml(rule.ruleCode)}"><div><strong>${escapeHtml(rule.ruleCode)} · ${escapeHtml(rule.ruleName)}</strong><small>${escapeHtml(rule.description)}</small></div><label><span class="field-label">Penalty</span><input name="penaltyPoints" type="number" min="0" max="10" step="0.5" value="${escapeHtml(rule.penaltyPoints)}"></label><label class="rule-enabled"><span class="field-label">Enabled</span><input name="enabled" type="checkbox" ${rule.enabled ? 'checked' : ''}></label><button class="button button-secondary" type="submit">Save rule</button></form>`).join('')}</div>`;
  }

  return emptyState('Select a view from the navigation.');
}

async function renderView() {
  if (!state.user) return;
  renderNavigation();
  const titles = {
    dashboard: 'Overview', submit: 'Submit Source Code', grades: 'My Grades', history: 'Submission History',
    analysis: 'Analysis & Fixes', rules: 'Rule Engine', 'edit-grade': 'Edit Grades', feedback: 'Manual Feedback',
    assignments: 'Assignments', submissions: 'Submissions', plagiarism: 'Code similarity', reports: 'Reports',
    classes: 'Classes', settings: 'Account settings'
  };
  document.querySelector('#view-title').textContent = titles[state.view] || 'Workspace';
  document.querySelector('#view-kicker').textContent = `${state.user.role.toUpperCase()} WORKSPACE`;
  workspace.innerHTML = '<p class="loading">Loading workspace data...</p>';
  try {
    if (state.view === 'settings') {
      const payload = await api('/api/me');
      state.profile = payload.user;
      workspace.innerHTML = renderAccountSettings(state.profile);
    } else {
      workspace.innerHTML = state.user.role.toLowerCase() === 'student'
        ? await renderStudentView()
        : await renderTeacherView();
    }
  } catch (error) {
    workspace.innerHTML = `<div class="notice">${escapeHtml(error.message)}</div>`;
  }
}

document.querySelector('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const errorElement = document.querySelector('#login-error');
  errorElement.textContent = '';
  try {
    const result = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        username: form.get('username'),
        passwordHash: form.get('passwordHash')
      })
    });
    setSignedIn(result.user, result.token);
  } catch (error) {
    errorElement.textContent = error.message;
  }
});

document.querySelector('#show-password-reset').addEventListener('click', () => {
  document.querySelector('#login-form').hidden = true;
  document.querySelector('#password-reset-form').hidden = false;
  document.querySelector('#reset-email').focus();
});

document.querySelector('#back-to-login').addEventListener('click', () => {
  document.querySelector('#password-reset-form').hidden = true;
  document.querySelector('#login-form').hidden = false;
  document.querySelector('#username').focus();
});

document.querySelector('#password-reset-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const errorElement = document.querySelector('#reset-error');
  errorElement.textContent = '';
  if (form.get('newPassword') !== form.get('confirmPassword')) {
    errorElement.textContent = 'Passwords do not match.';
    return;
  }
  try {
    await api('/api/auth/password-reset', {
      method: 'POST',
      body: JSON.stringify({
        email: form.get('email'),
        confirmationKey: form.get('confirmationKey'),
        newPassword: form.get('newPassword')
      })
    });
    formElement.reset();
    document.querySelector('#password-reset-form').hidden = true;
    document.querySelector('#login-form').hidden = false;
    document.querySelector('#login-error').textContent = 'Password updated. Sign in with your new password.';
    document.querySelector('#username').focus();
  } catch (error) {
    errorElement.textContent = error.message;
  }
});

document.querySelector('#logout-button').addEventListener('click', async () => {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* Clear local session even if the server has restarted. */ }
  state.token = null;
  state.user = null;
  portalScreen.hidden = true;
  loginScreen.hidden = false;
  document.querySelector('#login-form').reset();
});

document.querySelector('#main-nav').addEventListener('click', (event) => {
  const button = event.target.closest('[data-view]');
  if (!button) return;
  state.view = button.dataset.view;
  renderView();
});

workspace.addEventListener('click', async (event) => {
  const classTab = event.target.closest('[data-class-tab]');
  if (classTab) {
    state.selectedClassTab = classTab.dataset.classTab;
    state.editingAssignmentId = null;
    renderView();
    return;
  }

  const semesterButton = event.target.closest('[data-semester]');
  if (semesterButton) {
    state.selectedSemester = semesterButton.dataset.semester;
    renderView();
    return;
  }

  const courseButton = event.target.closest('.course-card[data-course-code]');
  if (courseButton) {
    state.selectedCourseCode = courseButton.dataset.courseCode;
    renderView();
    return;
  }

  if (event.target.closest('#back-to-courses')) {
    state.selectedCourseCode = null;
    renderView();
    return;
  }

  const removeStudent = event.target.closest('[data-remove-student]');
  if (removeStudent) {
    try {
      await api(`/api/teacher/classes/${encodeURIComponent(state.selectedClassCode)}/enrollments/${encodeURIComponent(removeStudent.dataset.removeStudent)}`, {
        method: 'PATCH', body: JSON.stringify({ enrolled: false })
      });
      showToast('Student removed from class.');
      await renderView();
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const readNotification = event.target.closest('[data-read-notification]');
  if (readNotification) {
    try {
      await api(`/api/me/notifications/${encodeURIComponent(readNotification.dataset.readNotification)}/read`, { method: 'PATCH' });
      await renderView();
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const studentViewButton = event.target.closest('[data-view]');
  if (studentViewButton) {
    state.view = studentViewButton.dataset.view;
    state.selectedAssignmentId = null;
    renderView();
    return;
  }

  if (event.target.closest('#back-to-student-assignments')) {
    state.selectedAssignmentId = null;
    renderView();
    return;
  }

  const viewStudentAssignment = event.target.closest('[data-view-student-assignment]');
  if (viewStudentAssignment) {
    const assignmentId = viewStudentAssignment.dataset.viewStudentAssignment;
    try {
      await api(`/api/student/assignments/${encodeURIComponent(assignmentId)}/read`, { method: 'PATCH' });
      state.selectedAssignmentId = assignmentId;
      state.view = 'assignments';
      await renderView();
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const editAssignment = event.target.closest('[data-edit-assignment]');
  if (editAssignment) {
    state.editingAssignmentId = editAssignment.dataset.editAssignment;
    renderView();
    return;
  }

  const reviewAssignment = event.target.closest('[data-review-assignment]');
  if (reviewAssignment) {
    state.selectedTeacherAssignmentId = reviewAssignment.dataset.reviewAssignment;
    state.selectedTeacherSubmissionId = null;
    state.view = 'submissions';
    renderView();
    return;
  }

  const reviewSubmission = event.target.closest('[data-review-submission]');
  if (reviewSubmission) {
    state.selectedTeacherSubmissionId = reviewSubmission.dataset.reviewSubmission;
    renderView();
    return;
  }

  if (event.target.closest('[data-back-to-submission-list]')) {
    state.selectedTeacherAssignmentId = null;
    renderView();
    return;
  }

  if (event.target.closest('[data-back-to-submission-roster]')) {
    state.selectedTeacherSubmissionId = null;
    renderView();
    return;
  }

  const analyzeButton = event.target.closest('[data-analyze-submission]');
  if (analyzeButton) {
    analyzeButton.disabled = true;
    try {
      await api(`/api/teacher/submissions/${encodeURIComponent(analyzeButton.dataset.analyzeSubmission)}/analyze`, { method: 'POST' });
      showToast('Analysis completed and score updated.');
      await renderView();
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const downloadButton = event.target.closest('[data-download-submission]');
  if (downloadButton) {
    const owner = downloadButton.dataset.owner;
    const endpoint = owner === 'teacher' ? '/api/teacher' : '/api/student';
    try {
      const response = await fetch(`${endpoint}/submissions/${encodeURIComponent(downloadButton.dataset.downloadSubmission)}/file`, {
        headers: { Authorization: `Bearer ${state.token}` }
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(payload?.error || 'Could not download submitted file.');
      const blob = await response.blob();
      const disposition = response.headers.get('content-disposition') ?? '';
      const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
      const fileName = encodedName ? decodeURIComponent(encodedName) : 'submission-file';
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = fileName;
      link.click();
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const toggleAssignment = event.target.closest('[data-toggle-assignment]');
  if (toggleAssignment) {
    try {
      await api(`/api/teacher/assignments/${encodeURIComponent(toggleAssignment.dataset.toggleAssignment)}`, {
        method: 'PATCH', body: JSON.stringify({ closed: toggleAssignment.dataset.closed !== 'true' })
      });
      showToast(toggleAssignment.dataset.closed === 'true' ? 'Assignment reopened.' : 'Assignment closed.');
      await renderView();
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  if (event.target.closest('#cancel-assignment-edit')) {
    state.editingAssignmentId = null;
    renderView();
    return;
  }

  const deleteAssignment = event.target.closest('[data-delete-assignment]');
  if (deleteAssignment) {
    if (!window.confirm('Delete this assignment?')) return;
    try {
      await api(`/api/teacher/assignments/${encodeURIComponent(deleteAssignment.dataset.deleteAssignment)}`, { method: 'DELETE' });
      state.editingAssignmentId = null;
      showToast('Assignment deleted.');
      await renderView();
    } catch (error) {
      showToast(error.message);
    }
    return;
  }

  const runButton = event.target.closest('.run-sandbox');
  if (runButton) {
    runButton.disabled = true;
    try {
      const result = await api(`/api/student/submissions/${encodeURIComponent(runButton.dataset.submissionId)}/run`, { method: 'POST' });
      const sandbox = result.sandbox;
      showToast(sandbox.available ? (sandbox.timedOut ? 'Sandbox execution timed out.' : `Sandbox exited with code ${sandbox.exitCode}.`) : sandbox.message);
      if (sandbox.stdout || sandbox.stderr) {
        const output = [sandbox.stdout, sandbox.stderr].filter(Boolean).join('\n');
        const panel = document.createElement('pre');
        panel.className = 'notice';
        panel.textContent = output;
        runButton.after(panel);
      }
    } catch (error) {
      showToast(error.message);
    } finally {
      runButton.disabled = false;
    }
    return;
  }

  if (event.target.closest('#print-report')) window.print();
  if (event.target.closest('#export-grades-csv')) {
    try {
      await downloadTeacherExport('/api/teacher/report.csv', 'teacher-grades.csv');
    } catch (error) {
      showToast(error.message);
    }
    return;
  }
  if (event.target.closest('#export-grades-xlsx')) {
    try {
      await downloadTeacherExport('/api/teacher/report.xlsx', 'teacher-grades.xlsx');
    } catch (error) {
      showToast(error.message);
    }
  }
});

async function downloadTeacherExport(url, fileName) {
  const response = await fetch(url, { headers: { Authorization: `Bearer ${state.token}` } });
  if (!response.ok) throw new Error('Could not export grades.');
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(objectUrl);
}

workspace.addEventListener('input', (event) => {
  if (event.target.id !== 'course-search') return;
  const search = event.target.value.trim().toLowerCase();
  workspace.querySelectorAll('[data-course-code]').forEach((card) => {
    card.hidden = !card.textContent.toLowerCase().includes(search);
  });
});

workspace.addEventListener('change', (event) => {
  if (event.target.id === 'class-course-select') {
    state.selectedClassCode = event.target.value;
    renderView();
    return;
  }
  if (event.target.id === 'plagiarism-assignment') {
    state.plagiarismAssignmentId = event.target.value;
    renderView();
    return;
  }
  if (event.target.id === 'feedback-course') {
    state.selectedCourseCode = event.target.value;
    state.selectedStudentId = null;
    renderView();
    return;
  }
  if (event.target.id === 'feedback-semester') {
    state.selectedSemester = event.target.value;
    state.selectedStudentId = null;
    renderView();
    return;
  }
  if (event.target.id === 'feedback-student') {
    state.selectedStudentId = event.target.value;
    renderView();
    return;
  }
  if (event.target.id === 'course-grade-course') {
    state.selectedCourseCode = event.target.value;
    state.selectedStudentId = null;
    renderView();
    return;
  }
  if (event.target.id === 'course-grade-semester') {
    state.selectedSemester = event.target.value;
    state.selectedStudentId = null;
    renderView();
    return;
  }
  if (event.target.id === 'course-grade-student') {
    state.selectedStudentId = event.target.value;
    renderView();
  }
});

workspace.addEventListener('submit', async (event) => {
  const form = event.target;
  event.preventDefault();
  try {
    if (form.id === 'profile-form') {
      const values = Object.fromEntries(new FormData(form));
      const result = await api('/api/me/profile', { method: 'PATCH', body: JSON.stringify(values) });
      state.profile = result.user;
      state.user = { ...state.user, fullName: result.user.fullName };
      document.querySelector('#account-name').textContent = result.user.fullName;
      document.querySelector('#account-initial').textContent = result.user.fullName.slice(0, 1).toUpperCase();
      showToast('Profile saved.');
      await renderView();
      return;
    }

    if (form.id === 'password-form') {
      const values = Object.fromEntries(new FormData(form));
      if (values.newPassword !== values.confirmPassword) throw new Error('Passwords do not match.');
      await api('/api/me/password', { method: 'PATCH', body: JSON.stringify(values) });
      form.reset();
      showToast('Password updated.');
      return;
    }

    if (form.id === 'class-teachers-form') {
      const values = new FormData(form);
      const teacherIds = values.getAll('teacherIds');
      await api(`/api/teacher/classes/${encodeURIComponent(form.dataset.courseCode)}/teachers`, {
        method: 'PATCH', body: JSON.stringify({ teacherIds })
      });
      showToast('Teacher assignments saved.');
      await renderView();
      return;
    }

    if (form.id === 'enroll-student-form') {
      const values = new FormData(form);
      await api(`/api/teacher/classes/${encodeURIComponent(form.dataset.courseCode)}/enrollments/${encodeURIComponent(values.get('studentId'))}`, {
        method: 'PATCH', body: JSON.stringify({ enrolled: true })
      });
      showToast('Student enrolled.');
      await renderView();
      return;
    }

    if (form.id === 'upload-form') {
      const result = await api('/api/student/submissions', { method: 'POST', body: new FormData(form) });
      showToast(`Submitted ${result.submission.fileName}.`);
      state.view = 'analysis';
      await renderView();
      return;
    }

    if (form.id === 'course-grade-form') {
      const values = Object.fromEntries(new FormData(form));
      await api(`/api/teacher/course-grades/${encodeURIComponent(values.studentId)}`, {
        method: 'PATCH',
        body: JSON.stringify({
          courseCode: values.courseCode,
          semester: values.semester,
          totalScore: values.totalScore,
          deduction: values.deduction,
          gradingNotes: values.gradingNotes
        })
      });
      state.selectedStudentId = values.studentId;
      showToast('Course grade saved.');
      await renderView();
      return;
    }

    if (form.id === 'course-feedback-form') {
      const values = Object.fromEntries(new FormData(form));
      await api(`/api/teacher/course-grades/${encodeURIComponent(values.studentId)}/feedback`, {
        method: 'PATCH',
        body: JSON.stringify({
          courseCode: values.courseCode,
          semester: values.semester,
          manualFeedback: values.manualFeedback
        })
      });
      state.selectedStudentId = values.studentId;
      showToast('Manual feedback saved.');
      await renderView();
      return;
    }

    if (form.id === 'submission-grade-form') {
      const values = Object.fromEntries(new FormData(form));
      await api(`/api/teacher/submissions/${encodeURIComponent(form.dataset.submissionId)}/grade`, {
        method: 'PATCH', body: JSON.stringify(values)
      });
      showToast('Submission grade saved.');
      await renderView();
      return;
    }

    if (form.id === 'submission-feedback-form') {
      const values = Object.fromEntries(new FormData(form));
      await api(`/api/teacher/submissions/${encodeURIComponent(form.dataset.submissionId)}/feedback`, {
        method: 'PATCH', body: JSON.stringify(values)
      });
      showToast('Manual feedback saved.');
      await renderView();
      return;
    }

    if (form.id === 'assignment-form') {
      const values = Object.fromEntries(new FormData(form));
      const assignmentId = values.assignmentId;
      const payload = {
        title: values.title,
        courseCode: values.courseCode,
        description: values.description,
        dueAt: new Date(values.dueAt).toISOString()
      };
      await api(assignmentId
        ? `/api/teacher/assignments/${encodeURIComponent(assignmentId)}`
        : '/api/teacher/assignments', {
        method: assignmentId ? 'PATCH' : 'POST',
        body: JSON.stringify(payload)
      });
      state.assignmentCourseCode = values.courseCode;
      state.editingAssignmentId = null;
      showToast(assignmentId ? 'Assignment updated.' : 'Assignment created.');
      await renderView();
      return;
    }

    if (form.matches('[data-rule-code]')) {
      const values = new FormData(form);
      await api(`/api/teacher/rules/${encodeURIComponent(form.dataset.ruleCode)}`, {
        method: 'PATCH',
        body: JSON.stringify({ penaltyPoints: values.get('penaltyPoints'), enabled: values.get('enabled') === 'on' })
      });
      showToast('Rule updated.');
      await renderView();
    }
  } catch (error) {
    showToast(error.message);
  }
});