const state = { token: null, user: null, view: 'dashboard', submissions: [], rules: [] };
const loginScreen = document.querySelector('#login-screen');
const portalScreen = document.querySelector('#portal-screen');
const workspace = document.querySelector('#workspace-content');
const toast = document.querySelector('#toast');

const studentNavigation = [
  ['dashboard', 'Overview'],
  ['submit', 'Submit Source Code'],
  ['grades', 'My Grades'],
  ['history', 'Submission History'],
  ['analysis', 'Analysis & Fixes']
];
const teacherNavigation = [
  ['dashboard', 'Overview'],
  ['submissions', 'Class Submissions'],
  ['report', 'Summary Report'],
  ['rules', 'Rule Engine'],
  ['edit-grade', 'Edit Grades'],
  ['feedback', 'Manual Feedback'],
  ['progress', 'Analysis Progress']
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

async function renderStudentView() {
  const [submissionPayload, gradePayload] = await Promise.all([
    api('/api/student/submissions'), api('/api/student/grades')
  ]);
  state.submissions = submissionPayload.submissions;
  const grades = gradePayload.grades;

  if (state.view === 'dashboard') {
    const average = grades.length
      ? (grades.reduce((sum, grade) => sum + Number(grade.totalScore), 0) / grades.length).toFixed(2)
      : '—';
    return `${`<div class="metric-grid">${metric('SUBMISSIONS', state.submissions.length)}${metric('GRADED', grades.length)}${metric('AVERAGE SCORE', average)}${metric('NEEDS REVIEW', state.submissions.filter((item) => item.status === 'Needs Review').length)}</div>`}
      <div class="section-heading"><h2>Recent submissions</h2><p>Your work and results</p></div>
      ${table(['File', 'Submission ID', 'Status', 'Score', 'Submitted'], state.submissions.slice(0, 8))}`;
  }

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
    return `<div class="section-heading"><h2>My grades</h2><p>Only your submissions are shown</p></div>${rows ? `<div class="table-wrap"><table><thead><tr><th>File</th><th>Submission ID</th><th>Score</th><th>Deduction</th><th>Notes</th><th>Source</th></tr></thead><tbody>${rows}</tbody></table></div>` : emptyState('No grades are available yet.')}`;
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
  const [submissionPayload, reportPayload] = await Promise.all([
    api('/api/teacher/submissions'), api('/api/teacher/report')
  ]);
  state.submissions = submissionPayload.submissions;
  const report = reportPayload.report;

  if (state.view === 'dashboard') {
    return `<div class="metric-grid">${metric('STUDENTS', report.studentCount)}${metric('SUBMISSIONS', report.submissionCount)}${metric('GRADED', report.gradedSubmissionCount)}${metric('CLASS AVERAGE', report.classAverage ?? '—')}</div>
      <div class="section-heading"><h2>Latest submissions</h2><p>Class-wide overview</p></div>
      ${table(['Student', 'File', 'Submission ID', 'Status', 'Score', 'Submitted'], state.submissions.slice(0, 10), true)}`;
  }

  if (state.view === 'submissions' || state.view === 'progress') {
    return `<div class="section-heading"><h2>${state.view === 'progress' ? 'Analysis progress' : 'Class submissions'}</h2><p>${state.submissions.length} records</p></div>${table(['Student', 'File', 'Submission ID', 'Status', 'Score', 'Submitted'], state.submissions, true)}`;
  }

  if (state.view === 'report') {
    const rows = report.students.map((student) => `<tr><td>${escapeHtml(student.fullName)}</td><td>${escapeHtml(student.userId)}</td><td>${student.submissionCount}</td><td>${student.gradedCount}</td><td class="score">${student.averageScore === null ? '—' : formatScore(student.averageScore)}</td></tr>`).join('');
    return `<div class="section-heading"><h2>Class summary report</h2><button id="print-report" class="button button-secondary" type="button">Print / Save PDF</button></div>
      <div class="metric-grid">${metric('STUDENTS', report.studentCount)}${metric('SUBMISSIONS', report.submissionCount)}${metric('GRADED', report.gradedSubmissionCount)}${metric('CLASS AVERAGE', report.classAverage ?? '—')}</div>
      <div class="table-wrap"><table><thead><tr><th>Student</th><th>User ID</th><th>Submissions</th><th>Graded</th><th>Average</th></tr></thead><tbody>${rows}</tbody></table></div>`;
  }

  if (state.view === 'rules') {
    const payload = await api('/api/teacher/rules');
    state.rules = payload.rules;
    return `<div class="section-heading"><h2>Rule engine configuration</h2><p>Penalty range: 0 to 10 points</p></div>
      <div class="rule-list">${state.rules.map((rule) => `<form class="rule-row" data-rule-code="${escapeHtml(rule.ruleCode)}"><div><strong>${escapeHtml(rule.ruleCode)} · ${escapeHtml(rule.ruleName)}</strong><small>${escapeHtml(rule.description)}</small></div><label><span class="field-label">Penalty</span><input name="penaltyPoints" type="number" min="0" max="10" step="0.5" value="${escapeHtml(rule.penaltyPoints)}"></label><label class="rule-enabled"><span class="field-label">Enabled</span><input name="enabled" type="checkbox" ${rule.enabled ? 'checked' : ''}></label><button class="button button-secondary" type="submit">Save rule</button></form>`).join('')}</div>`;
  }

  if (state.view === 'edit-grade') {
    const options = state.submissions.map((submission) => `<option value="${escapeHtml(submission.submissionId)}">${escapeHtml(submission.studentName)} · ${escapeHtml(submission.fileName)} · ${escapeHtml(submission.submissionId)}</option>`).join('');
    return `<div class="section-heading"><h2>Edit grades</h2><p>Teacher changes are tracked separately from automated scoring</p></div>
      ${options ? `<form id="grade-form" class="content-form"><label class="field-label" for="grade-submission">Submission</label><select id="grade-submission" name="submissionId" required>${options}</select><div class="field-row"><label class="field-label">Score<input name="totalScore" type="number" min="0" max="10" step="0.1" required></label><label class="field-label">Deduction<input name="deduction" type="number" min="0" max="10" step="0.1" value="0" required></label></div><label class="field-label">Grading notes<textarea name="gradingNotes" maxlength="2000"></textarea></label><button class="button button-primary" type="submit">Save grade</button></form>` : emptyState('There are no submissions to grade.')}`;
  }

  if (state.view === 'feedback') {
    const options = state.submissions.map((submission) => `<option value="${escapeHtml(submission.submissionId)}">${escapeHtml(submission.studentName)} · ${escapeHtml(submission.fileName)} · ${escapeHtml(submission.submissionId)}</option>`).join('');
    return `<div class="section-heading"><h2>Manual feedback</h2><p>Feedback is separate from editing a grade</p></div>
      ${options ? `<form id="feedback-form" class="content-form"><label class="field-label">Submission<select name="submissionId" required>${options}</select></label><label class="field-label">Feedback<textarea name="feedback" minlength="1" maxlength="4000" required></textarea></label><button class="button button-primary" type="submit">Send feedback</button></form>` : emptyState('There are no submissions for feedback.')}`;
  }
  return emptyState('Select a view from the navigation.');
}

async function renderView() {
  if (!state.user) return;
  renderNavigation();
  const titles = {
    dashboard: 'Overview', submit: 'Submit Source Code', grades: 'My Grades', history: 'Submission History',
    analysis: 'Analysis & Fixes', submissions: 'Class Submissions', report: 'Summary Report', rules: 'Rule Engine',
    'edit-grade': 'Edit Grades', feedback: 'Manual Feedback', progress: 'Analysis Progress'
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
        passwordHash: form.get('passwordHash'),
        role: form.get('role')
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

    if (form.id === 'grade-form') {
      const values = Object.fromEntries(new FormData(form));
      await api(`/api/teacher/submissions/${encodeURIComponent(values.submissionId)}/grade`, {
        method: 'PATCH',
        body: JSON.stringify({ totalScore: values.totalScore, deduction: values.deduction, gradingNotes: values.gradingNotes })
      });
      showToast('Grade saved.');
      await renderView();
      return;
    }

    if (form.id === 'feedback-form') {
      const values = Object.fromEntries(new FormData(form));
      await api(`/api/teacher/submissions/${encodeURIComponent(values.submissionId)}/feedback`, {
        method: 'PATCH', body: JSON.stringify({ feedback: values.feedback })
      });
      showToast('Feedback saved.');
      form.reset();
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