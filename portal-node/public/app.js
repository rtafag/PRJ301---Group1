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
  editingAssignmentId: null
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
  ['rules', 'Rule Engine'],
  ['edit-grade', 'Edit Grades'],
  ['feedback', 'Manual Feedback'],
  ['assignments', 'Assignments']
];

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

function renderTeacherAssignments() {
  const selectedAssignment = state.assignments.find((assignment) => assignment.assignmentId === state.editingAssignmentId);
  if (state.editingAssignmentId && !selectedAssignment) state.editingAssignmentId = null;
  const defaultCourse = state.courses.some((course) => course.courseCode === state.assignmentCourseCode)
    ? state.assignmentCourseCode
    : selectedAssignment?.courseCode ?? state.courses[0]?.courseCode ?? '';
  state.assignmentCourseCode = defaultCourse;
  const courseOptions = state.courses.map((course) => `<option value="${escapeHtml(course.courseCode)}" ${course.courseCode === (selectedAssignment?.courseCode ?? defaultCourse) ? 'selected' : ''}>${escapeHtml(course.courseCode)}</option>`).join('');
  const assignmentRows = [...state.assignments].sort((first, second) => new Date(first.dueAt) - new Date(second.dueAt)).map((assignment) => `
    <tr>
      <td>${escapeHtml(assignment.courseCode)}</td>
      <td><strong>${escapeHtml(assignment.title)}</strong><small class="assignment-description">${escapeHtml(assignment.description || '')}</small></td>
      <td>${formatDateTime(assignment.dueAt)}</td>
      <td><span class="pill ${assignment.status === 'Expired' ? 'warn' : ''}">${escapeHtml(assignment.status)}</span></td>
      <td><div class="assignment-actions">
        <button class="button button-secondary" type="button" data-edit-assignment="${escapeHtml(assignment.assignmentId)}">Edit</button>
        <button class="button button-danger" type="button" data-delete-assignment="${escapeHtml(assignment.assignmentId)}">Delete</button>
      </div></td>
    </tr>`).join('');
  const formTitle = selectedAssignment ? 'Edit assignment' : 'Create assignment';
  return `<div class="section-heading"><h2>Assignments</h2><p>Create homework and manage deadlines</p></div>
    <form id="assignment-form" class="content-form">
      <input type="hidden" name="assignmentId" value="${escapeHtml(selectedAssignment?.assignmentId ?? '')}">
      <label class="field-label">Course<select name="courseCode" required>${courseOptions}</select></label>
      <label class="field-label">Title<input name="title" maxlength="120" value="${escapeHtml(selectedAssignment?.title ?? '')}" required></label>
      <label class="field-label">Description<textarea name="description" maxlength="4000">${escapeHtml(selectedAssignment?.description ?? '')}</textarea></label>
      <label class="field-label">Due date and time<input name="dueAt" type="datetime-local" value="${toLocalDateTime(selectedAssignment?.dueAt)}" required></label>
      <button class="button button-primary" type="submit">${formTitle}</button>
      ${selectedAssignment ? '<button id="cancel-assignment-edit" class="button button-secondary" type="button">Cancel</button>' : ''}
    </form>
    <div class="section-heading"><h2>Assignment list</h2><p>${state.assignments.length} assignments</p></div>
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

function renderStudentNotifications(assignments) {
  const unreadAssignments = assignments.filter((assignment) => !assignment.isRead);
  const notifications = unreadAssignments.map((assignment) => `
    <article class="student-notification">
      <div><strong>New assignment in ${escapeHtml(assignment.courseCode)}: ${escapeHtml(assignment.title)}</strong><p>Due ${formatDateTime(assignment.dueAt)} · ${escapeHtml(assignment.status)}</p></div>
      <button class="button button-secondary" type="button" data-view-student-assignment="${escapeHtml(assignment.assignmentId)}">View assignment</button>
    </article>`).join('');
  return `<section class="student-notifications">
    <div class="student-notifications-heading"><h2>Notifications</h2><span>${unreadAssignments.length} new</span></div>
    ${notifications || emptyState('No new assignments.')}
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
    const [coursePayload, gradePayload, assignmentPayload] = await Promise.all([
      api('/api/student/courses'), api('/api/student/course-grades'), api('/api/student/assignments')
    ]);
    state.courses = coursePayload.courses;
    state.semesters = coursePayload.semesters;
    state.studentAssignments = assignmentPayload.assignments;
    if (!state.semesters.some((semester) => semester.value === state.selectedSemester)) {
      state.selectedSemester = state.semesters[0]?.value ?? '';
    }
    if (!state.courses.some((course) => course.courseCode === state.selectedCourseCode)) {
      state.selectedCourseCode = null;
    }
    const selectedCourse = state.courses.find((course) => course.courseCode === state.selectedCourseCode);
    const notifications = renderStudentNotifications(state.studentAssignments);
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
    return `<div class="section-heading"><h2>Submit Java source code</h2><p>Java files only · 512 KB maximum</p></div>
      <form id="upload-form" class="content-form">
        <label class="field-label" for="source-file">Source file</label>
        <input id="source-file" name="sourceFile" type="file" accept=".java,text/x-java" required>
        <p class="login-note">The portal calculates a SHA-256 hash, parses Java syntax and applies enabled rules. Code is not executed unless the Docker sandbox is enabled.</p>
        <button class="button button-primary" type="submit">Submit and analyze</button>
      </form>`;
  }

  if (state.view === 'grades') {
    const rows = grades.map((grade) => `<tr><td>${escapeHtml(grade.fileName)}</td><td>${escapeHtml(grade.submissionId)}</td><td class="score">${formatScore(grade.totalScore)}</td><td>${formatScore(grade.deduction)}</td><td>${escapeHtml(grade.gradingNotes || '')}</td><td>${grade.manuallyEdited ? 'Teacher review' : 'Rule engine'}</td></tr>`).join('');
    return `${courseGrades.length ? `<div class="section-heading"><h2>Course grades</h2><p>${courseGrades.length} enrolled courses</p></div>${studentCourseGradeTable(courseGrades)}` : ''}
      ${rows ? `<div class="section-heading"><h2>Submission grades</h2><p>Scores for your submitted source code</p></div><div class="table-wrap"><table><thead><tr><th>File</th><th>Submission ID</th><th>Score</th><th>Deduction</th><th>Notes</th><th>Source</th></tr></thead><tbody>${rows}</tbody></table></div>` : (!courseGrades.length ? emptyState('No grades are available yet.') : '')}`;
  }

  if (state.view === 'history') {
    return `<div class="section-heading"><h2>Submission history</h2><p>${state.submissions.length} records</p></div>${table(['File', 'Submission ID', 'Status', 'Score', 'Submitted'], state.submissions)}`;
  }

  const analyses = state.submissions.filter((submission) => submission.analysis);
  if (!analyses.length) return `<div class="section-heading"><h2>Analysis &amp; fixes</h2></div>${emptyState('Analysis results will appear after you submit Java source code.')}`;
  return `<div class="section-heading"><h2>Analysis &amp; fixes</h2><p>Static analysis results for your submissions</p></div>
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
    assignments: 'Assignments'
  };
  document.querySelector('#view-title').textContent = titles[state.view] || 'Workspace';
  document.querySelector('#view-kicker').textContent = `${state.user.role.toUpperCase()} WORKSPACE`;
  workspace.innerHTML = '<p class="loading">Loading workspace data...</p>';
  try {
    workspace.innerHTML = state.user.role.toLowerCase() === 'student'
      ? await renderStudentView()
      : await renderTeacherView();
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
  const semesterButton = event.target.closest('[data-semester]');
  if (semesterButton) {
    state.selectedSemester = semesterButton.dataset.semester;
    renderView();
    return;
  }

  const courseButton = event.target.closest('[data-course-code]');
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
});

workspace.addEventListener('input', (event) => {
  if (event.target.id !== 'course-search') return;
  const search = event.target.value.trim().toLowerCase();
  workspace.querySelectorAll('[data-course-code]').forEach((card) => {
    card.hidden = !card.textContent.toLowerCase().includes(search);
  });
});

workspace.addEventListener('change', (event) => {
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