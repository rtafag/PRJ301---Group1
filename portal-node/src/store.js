import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'csv-parse/sync';
import { randomUUID } from 'node:crypto';

const moduleDirectory = path.dirname(fileURLToPath(import.meta.url));
export const portalDirectory = path.resolve(moduleDirectory, '..');
export const repositoryDirectory = path.resolve(portalDirectory, '..');
const runtimeDirectory = path.join(portalDirectory, 'storage');
const runtimeFile = path.join(runtimeDirectory, 'portal-data.json');

function readCsv(fileName) {
  const filePath = path.join(repositoryDirectory, 'data', fileName);
  try {
    return parse(fs.readFileSync(filePath, 'utf8'), {
      bom: true,
      columns: true,
      skip_empty_lines: true,
      trim: true
    });
  } catch {
    return [];
  }
}

function readRuntimeData() {
  try {
    return JSON.parse(fs.readFileSync(runtimeFile, 'utf8'));
  } catch {
    return null;
  }
}

function createSeedSubmissions() {
  const scores = new Map(readCsv('score.csv').map((row) => [row.submissionId, row]));
  const analyses = new Map(readCsv('analysis_result.csv').map((row) => [row.submissionId, row]));
  const violationsByAnalysis = new Map();

  for (const row of readCsv('rule_violation.csv')) {
    const violations = violationsByAnalysis.get(row.analysisId) ?? [];
    violations.push({
      ruleId: Number(row.ruleId),
      fileName: row.fileName,
      lineNumber: Number(row.lineNumber),
      suggestedFix: row.suggestedFix
    });
    violationsByAnalysis.set(row.analysisId, violations);
  }

  return readCsv('submission.csv').map((row) => {
    const score = scores.get(row.submissionId);
    const analysis = analyses.get(row.submissionId);
    return {
      submissionId: row.submissionId,
      userId: row.userId,
      fileName: path.basename(row.fileUrl || `${row.submissionId}.java`),
      fileUrl: row.fileUrl,
      fileHash: row.fileHash,
      status: row.status,
      submittedAt: row.submittedAt,
      sourceCode: null,
      score: score ? {
        totalScore: Number(score.totalScore),
        deduction: Number(score.deduction),
        gradingNotes: score.gradingNotes,
        updatedAt: score.updatedAt,
        manuallyEdited: false
      } : null,
      analysis: analysis ? {
        astJson: analysis.astJson,
        aiFeedback: analysis.aiFeedback,
        executionTimeMs: Number(analysis.executionTimeMs),
        violations: violationsByAnalysis.get(analysis.analysisId) ?? []
      } : null,
      manualFeedback: ''
    };
  });
}

function safeNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function sourceShingles(source) {
  const uncommented = String(source).replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/.*$/gm, ' ');
  const tokens = (uncommented.match(/[A-Za-z_$][\w$]*|\d+(?:\.\d+)?|[^\s]/g) ?? []).slice(0, 20000);
  const shingles = new Set();
  for (let index = 0; index <= tokens.length - 5; index += 1) {
    shingles.add(tokens.slice(index, index + 5).join(' '));
  }
  return shingles;
}

function jaccardSimilarity(first, second) {
  if (!first.size || !second.size) return 0;
  let overlap = 0;
  for (const shingle of first) if (second.has(shingle)) overlap += 1;
  return overlap / (first.size + second.size - overlap);
}

export function createStore({ runtimeData: initialRuntimeData, saveRuntimeData } = {}) {
  const users = readCsv('users.csv').map((row) => ({
    userId: row.userId,
    username: row.username,
    passwordHash: row.passwordHash,
    fullName: row.fullName,
    email: row.email,
    role: row.role
  }));
  const runtime = initialRuntimeData ?? readRuntimeData();
  const profileOverrides = runtime?.profileOverrides && typeof runtime.profileOverrides === 'object'
    ? runtime.profileOverrides
    : {};
  for (const user of users) {
    Object.assign(user, profileOverrides[user.userId] ?? {});
  }
  const teacherUsers = users.filter((user) => user.role.toLowerCase() === 'teacher');
  const studentUsers = users.filter((user) => user.role.toLowerCase() === 'student');
  const academicStudents = readCsv('students.csv').map((row, index) => ({
    studentId: studentUsers[index]?.userId ?? row.UserID,
    sourceStudentId: row.UserID,
    fullName: studentUsers[index]?.fullName ?? row.Name,
    gpa: Number(row.GPA),
    courseCodes: runtime?.classManagement?.enrollments?.[studentUsers[index]?.userId ?? row.UserID]
      ?? String(row.CourseCode ?? '').split('|').filter(Boolean),
    status: 'active'
  }));
  const academicTeachers = readCsv('teachers.csv').map((row, index) => ({
    ...row,
    teacherId: teacherUsers[index]?.userId ?? row.UserID,
    sourceTeacherId: row.UserID,
    courseCodes: String(row.CourseCode ?? '').split('|').filter(Boolean)
  }));
  const teacherProfileByUser = new Map(academicTeachers.map((teacher) => [teacher.teacherId, teacher]));
  const teacherCourseAssignments = new Map(academicTeachers.map((teacher) => [teacher.teacherId, [...teacher.courseCodes]]));
  const courseTeacherAssignments = new Map();
  for (const teacher of academicTeachers) {
    for (const courseCode of teacher.courseCodes) {
      const teacherIds = courseTeacherAssignments.get(courseCode) ?? [];
      teacherIds.push(teacher.teacherId);
      courseTeacherAssignments.set(courseCode, teacherIds);
    }
  }
  for (const [courseCode, teacherIds] of Object.entries(runtime?.classManagement?.courseTeachers ?? {})) {
    const validTeacherIds = teacherIds.filter((teacherId) => teacherUsers.some((teacher) => teacher.userId === teacherId));
    courseTeacherAssignments.set(courseCode, validTeacherIds);
    for (const teacher of teacherUsers) {
      teacherCourseAssignments.set(teacher.userId, (teacherCourseAssignments.get(teacher.userId) ?? [])
        .filter((item) => item !== courseCode));
    }
    for (const teacherId of validTeacherIds) {
      teacherCourseAssignments.get(teacherId)?.push(courseCode);
    }
  }
  const courseCodes = [...new Set([
    ...academicStudents.flatMap((student) => student.courseCodes),
    ...academicTeachers.flatMap((teacher) => String(teacher.CourseCode ?? '').split('|').filter(Boolean)),
    ...courseTeacherAssignments.keys()
  ])].sort();
  const studentIdBySourceId = new Map(academicStudents.map((student) => [student.sourceStudentId, student.studentId]));
  const teacherIdBySourceId = new Map(academicTeachers.map((teacher) => [teacher.sourceTeacherId, teacher.teacherId]));
  const legacyTeacherIdByUserId = new Map(teacherUsers.slice(0, 10).map((user, index) => [
    `U${String(index + 1).padStart(4, '0')}`,
    user.userId
  ]));
  const courseTeacherByStudent = new Map();
  for (const courseCode of courseCodes) {
    const teachers = courseTeacherAssignments.get(courseCode) ?? [];
    const enrolledStudents = academicStudents.filter((student) => student.courseCodes.includes(courseCode));
    const studentAssignments = new Map();
    enrolledStudents.forEach((student, index) => {
      if (teachers.length) studentAssignments.set(student.studentId, teachers[index % teachers.length]);
    });
    courseTeacherByStudent.set(courseCode, studentAssignments);
  }
  function assignedTeacher(courseCode, studentId) {
    return courseTeacherByStudent.get(courseCode)?.get(studentId) ?? null;
  }
  const semesters = [
    { value: 'trial', season: 'TRIAL', year: null },
    { value: 'summer-2026', season: 'SUMMER', year: 2026 },
    { value: 'summer-2025', season: 'SUMMER', year: 2025 },
    { value: 'spring-2026', season: 'SPRING', year: 2026 },
    { value: 'fall-2026', season: 'FALL', year: 2026 },
    { value: 'fall-2025', season: 'FALL', year: 2025 }
  ];
  function createCourseGrades() {
    return academicStudents.flatMap((student, studentIndex) => student.courseCodes.map((courseCode) => {
      const courseIndex = courseCodes.indexOf(courseCode);
      const gradeTenths = 40 + ((Math.round(student.gpa * 10) + studentIndex + courseIndex * 7) % 61);
      const totalScore = Number((gradeTenths / 10).toFixed(1));
      return {
        studentId: student.studentId,
        studentName: student.fullName,
        courseCode,
        teacherId: assignedTeacher(courseCode, student.studentId),
        semester: 'fall-2026',
        totalScore,
        deduction: Number((10 - totalScore).toFixed(1)),
        gradingNotes: '',
        manualFeedback: '',
        updatedAt: null,
        active: student.status === 'active'
      };
    }));
  }
  const csvRules = readCsv('rule_definition.csv').map((row) => ({
    ruleId: safeNumber(row.ruleId),
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    severity: row.severity,
    penaltyPoints: safeNumber(row.penaltyPoints),
    description: row.description,
    enabled: true
  }));
  let gradeAuditLog = Array.isArray(runtime?.gradeAuditLog) ? runtime.gradeAuditLog : [];
  let notifications = Array.isArray(runtime?.notifications) ? runtime.notifications : [];
  let notificationReads = runtime?.notificationReads && typeof runtime.notificationReads === 'object'
    ? runtime.notificationReads
    : {};
  const classManagement = runtime?.classManagement && typeof runtime.classManagement === 'object'
    ? runtime.classManagement
    : { enrollments: {}, courseTeachers: {} };
  if (!classManagement.enrollments || typeof classManagement.enrollments !== 'object') classManagement.enrollments = {};
  if (!classManagement.courseTeachers || typeof classManagement.courseTeachers !== 'object') classManagement.courseTeachers = {};
  const passwordOverrides = runtime?.passwordOverrides && typeof runtime.passwordOverrides === 'object'
    ? { ...runtime.passwordOverrides }
    : {};
  for (const user of users) {
    if (typeof passwordOverrides[user.userId] === 'string') {
      user.passwordHash = passwordOverrides[user.userId];
    }
  }
  let submissions = Array.isArray(runtime?.submissions) ? runtime.submissions : createSeedSubmissions();
  let rules = Array.isArray(runtime?.rules)
    ? [...runtime.rules, ...csvRules.filter((rule) => !runtime.rules.some((stored) => stored.ruleCode === rule.ruleCode))]
    : csvRules;
  let courseGrades = Array.isArray(runtime?.courseGrades) ? runtime.courseGrades : createCourseGrades();
  let assignments = Array.isArray(runtime?.assignments) ? runtime.assignments : [];
  let persistenceQueue = Promise.resolve();
  for (const grade of courseGrades) {
    grade.studentId = studentIdBySourceId.get(grade.studentId) ?? grade.studentId;
    grade.teacherId = teacherIdBySourceId.get(grade.teacherId)
      ?? legacyTeacherIdByUserId.get(grade.teacherUserId)
      ?? grade.teacherId;
    if (typeof grade.manualFeedback !== 'string') grade.manualFeedback = '';
    delete grade.teacherUserId;
    grade.teacherId = assignedTeacher(grade.courseCode, grade.studentId);
  }
  for (const assignment of assignments) {
    assignment.teacherId = legacyTeacherIdByUserId.get(assignment.teacherId) ?? assignment.teacherId;
    if (!Array.isArray(assignment.readBy)) assignment.readBy = [];
  }

  function getRuntimeData() {
    return {
      submissions, rules, courseGrades, assignments, passwordOverrides, gradeAuditLog,
      classManagement, profileOverrides, notifications, notificationReads
    };
  }

  function persist() {
    const runtimeData = getRuntimeData();
    if (saveRuntimeData) {
      const snapshot = JSON.parse(JSON.stringify(runtimeData));
      persistenceQueue = persistenceQueue.catch(() => {}).then(() => saveRuntimeData(snapshot));
      return;
    }
    fs.mkdirSync(runtimeDirectory, { recursive: true });
    const temporaryFile = `${runtimeFile}.tmp`;
    fs.writeFileSync(temporaryFile, JSON.stringify(runtimeData, null, 2), 'utf8');
    fs.renameSync(temporaryFile, runtimeFile);
  }

  return {
    getRuntimeData,
    flushPersistence() {
      return persistenceQueue;
    },
    users,
    findUser(username) {
      return users.find((user) => user.username === username) ?? null;
    },
    findUserById(userId) {
      return users.find((user) => user.userId === userId) ?? null;
    },
    updateUserProfile(userId, changes) {
      const user = users.find((item) => item.userId === userId);
      if (!user) return null;
      Object.assign(user, changes);
      const student = academicStudents.find((item) => item.studentId === userId);
      if (student && changes.fullName) student.fullName = changes.fullName;
      profileOverrides[userId] = { fullName: user.fullName, email: user.email };
      persist();
      return { userId, username: user.username, fullName: user.fullName, email: user.email, role: user.role };
    },
    updateUserPassword(userId, passwordHash) {
      const user = users.find((item) => item.userId === userId);
      if (!user) return false;
      user.passwordHash = passwordHash;
      passwordOverrides[userId] = passwordHash;
      persist();
      return true;
    },
    listStudents() {
      return academicStudents.map((student) => ({ studentId: student.studentId, fullName: student.fullName, courseCodes: [...student.courseCodes] }));
    },
    listTeachers() {
      return teacherUsers.map((teacher) => ({ userId: teacher.userId, fullName: teacher.fullName, email: teacher.email }));
    },
    listClassRoster(userId, courseCode) {
      if (!(teacherCourseAssignments.get(userId) ?? []).includes(courseCode)) return null;
      return academicStudents.filter((student) => student.courseCodes.includes(courseCode))
        .map((student) => ({ studentId: student.studentId, fullName: student.fullName, email: users.find((user) => user.userId === student.studentId)?.email }));
    },
    listClassManagement(userId) {
      return this.listCoursesForTeacher(userId).map((course) => ({
        ...course,
        teacherIds: [...(courseTeacherAssignments.get(course.courseCode) ?? [])],
        roster: this.listClassRoster(userId, course.courseCode) ?? []
      }));
    },
    updateEnrollment(userId, courseCode, studentId, enrolled) {
      if (!(teacherCourseAssignments.get(userId) ?? []).includes(courseCode)) return null;
      const student = academicStudents.find((item) => item.studentId === studentId);
      if (!student) return false;
      const enrolledNow = student.courseCodes.includes(courseCode);
      if (enrolled && !enrolledNow) student.courseCodes.push(courseCode);
      if (!enrolled && enrolledNow) student.courseCodes = student.courseCodes.filter((item) => item !== courseCode);
      classManagement.enrollments[studentId] = [...student.courseCodes];
      if (enrolled) {
        const teachers = courseTeacherAssignments.get(courseCode) ?? [];
        const rosterIndex = academicStudents.filter((item) => item.courseCodes.includes(courseCode)).indexOf(student);
        if (teachers.length) courseTeacherByStudent.get(courseCode)?.set(studentId, teachers[rosterIndex % teachers.length]);
      }
      if (enrolled && !courseGrades.some((grade) => grade.studentId === studentId && grade.courseCode === courseCode && grade.semester === 'fall-2026')) {
        const studentIndex = academicStudents.indexOf(student);
        const courseIndex = courseCodes.indexOf(courseCode);
        const totalScore = Number((4 + ((Math.round(student.gpa * 10) + studentIndex + courseIndex * 7) % 61) / 10).toFixed(1));
        courseGrades.push({
          studentId, studentName: student.fullName, courseCode, teacherId: assignedTeacher(courseCode, studentId),
          semester: 'fall-2026', totalScore, deduction: Number((10 - totalScore).toFixed(1)),
          gradingNotes: '', manualFeedback: '', updatedAt: null, active: true
        });
      }
      if (!enrolled) courseGrades = courseGrades.filter((grade) => !(grade.studentId === studentId && grade.courseCode === courseCode));
      persist();
      return true;
    },
    updateCourseTeachers(userId, courseCode, teacherIds) {
      if (!(teacherCourseAssignments.get(userId) ?? []).includes(courseCode)) return null;
      if (!Array.isArray(teacherIds) || !teacherIds.length
          || teacherIds.some((teacherId) => !teacherUsers.some((teacher) => teacher.userId === teacherId))) return false;
      courseTeacherAssignments.set(courseCode, [...new Set(teacherIds)]);
      for (const teacher of teacherUsers) {
        const courses = teacherCourseAssignments.get(teacher.userId) ?? [];
        teacherCourseAssignments.set(teacher.userId, courses.filter((item) => item !== courseCode));
      }
      for (const teacherId of courseTeacherAssignments.get(courseCode)) {
        teacherCourseAssignments.get(teacherId)?.push(courseCode);
      }
      for (const student of academicStudents.filter((item) => item.courseCodes.includes(courseCode))) {
        const teachers = courseTeacherAssignments.get(courseCode);
        const index = academicStudents.filter((item) => item.courseCodes.includes(courseCode)).indexOf(student);
        courseTeacherByStudent.get(courseCode)?.set(student.studentId, teachers[index % teachers.length]);
      }
      for (const grade of courseGrades.filter((item) => item.courseCode === courseCode)) {
        grade.teacherId = assignedTeacher(courseCode, grade.studentId);
      }
      classManagement.courseTeachers[courseCode] = [...courseTeacherAssignments.get(courseCode)];
      persist();
      return true;
    },
    createNotification(userId, notification) {
      const created = { notificationId: randomUUID(), userId, ...notification, createdAt: new Date().toISOString() };
      notifications.unshift(created);
      persist();
      return created;
    },
    listNotifications(userId) {
      const explicit = notifications.filter((item) => item.userId === userId).map((item) => ({
        ...item, isRead: (notificationReads[userId] ?? []).includes(item.notificationId)
      }));
      const deadlines = this.listAssignmentsForStudent(userId)
        .filter((assignment) => !assignment.closed && new Date(assignment.dueAt).getTime() > Date.now()
          && new Date(assignment.dueAt).getTime() - Date.now() <= 48 * 60 * 60 * 1000)
        .map((assignment) => ({
          notificationId: `deadline:${assignment.assignmentId}`,
          userId,
          type: 'deadline',
          title: `Deadline approaching: ${assignment.title}`,
          message: `${assignment.courseCode} · Due ${assignment.dueAt}`,
          createdAt: new Date().toISOString(),
          isRead: (notificationReads[userId] ?? []).includes(`deadline:${assignment.assignmentId}`)
        }));
      return [...deadlines, ...explicit].sort((first, second) => new Date(second.createdAt) - new Date(first.createdAt));
    },
    markNotificationRead(userId, notificationId) {
      const exists = this.listNotifications(userId).some((item) => item.notificationId === notificationId);
      if (!exists) return false;
      const reads = notificationReads[userId] ?? [];
      if (!reads.includes(notificationId)) notificationReads[userId] = [...reads, notificationId];
      persist();
      return true;
    },
    resetPassword(email, confirmationKey, passwordHash) {
      const user = users.find((item) => item.email.trim().toLowerCase() === email
        && item.userId === confirmationKey);
      if (!user) return false;
      user.passwordHash = passwordHash;
      passwordOverrides[user.userId] = passwordHash;
      persist();
      return true;
    },
    listSubmissions() {
      return [...submissions];
    },
    listSubmissionsForTeacher(userId) {
      const teacherCourses = teacherCourseAssignments.get(userId) ?? [];
      const students = new Set(academicStudents
        .filter((student) => student.courseCodes.some((courseCode) => teacherCourses.includes(courseCode)))
        .map((student) => student.studentId));
      return submissions.filter((submission) => students.has(submission.userId));
    },
    listSubmissionsForUser(userId) {
      return submissions.filter((submission) => submission.userId === userId);
    },
    listCourseGradesForStudent(studentId) {
      return courseGrades.filter((grade) => grade.studentId === studentId);
    },
    listCoursesForStudent(studentId) {
      const student = academicStudents.find((item) => item.studentId === studentId);
      if (!student) return [];
      return student.courseCodes.map((courseCode) => ({
        courseCode,
        courseName: courseCode,
        active: true
      }));
    },
    listAssignmentsForStudent(studentId) {
      const student = academicStudents.find((item) => item.studentId === studentId);
      if (!student) return [];
      return assignments
        .filter((assignment) => student.courseCodes.includes(assignment.courseCode))
        .map((assignment) => ({ ...assignment, isRead: assignment.readBy.includes(studentId) }));
    },
    listStudentsForAssignment(userId, assignmentId) {
      const assignment = this.findAssignmentForTeacher(userId, assignmentId);
      if (!assignment) return null;
      return academicStudents
        .filter((student) => student.courseCodes.includes(assignment.courseCode))
        .map((student) => ({
          studentId: student.studentId,
          fullName: student.fullName,
          submission: submissions.find((submission) => submission.assignmentId === assignmentId
            && submission.userId === student.studentId) ?? null
        }));
    },
    markAssignmentRead(studentId, assignmentId) {
      const student = academicStudents.find((item) => item.studentId === studentId);
      const assignment = assignments.find((item) => item.assignmentId === assignmentId);
      if (!student || !assignment || !student.courseCodes.includes(assignment.courseCode)) return null;
      if (!assignment.readBy.includes(studentId)) {
        assignment.readBy.push(studentId);
        persist();
      }
      return { ...assignment, isRead: true };
    },
    listSemesters() {
      return semesters.map((semester) => ({ ...semester }));
    },
    listCoursesForTeacher(userId) {
      const teacher = teacherProfileByUser.get(userId);
      const assignedCourses = teacherCourseAssignments.get(userId) ?? [];
      const seedOrder = teacher?.courseCodes ?? [];
      const orderedCourses = [
        ...seedOrder.filter((courseCode) => assignedCourses.includes(courseCode)),
        ...assignedCourses.filter((courseCode) => !seedOrder.includes(courseCode))
      ];
      return orderedCourses
        .map((courseCode) => ({
          courseCode,
          courseName: courseCode,
          studentCount: courseGrades.filter((grade) => grade.courseCode === courseCode
            && grade.teacherId === teacher.teacherId && grade.semester === 'fall-2026').length,
          active: true
        }));
    },
    listCourseGrades(userId, courseCode, semester) {
      const teacher = teacherProfileByUser.get(userId);
      if (!teacher || !(teacherCourseAssignments.get(userId) ?? []).includes(courseCode)
          || !semesters.some((item) => item.value === semester)) return null;
      return courseGrades.filter((grade) => grade.teacherId === teacher.teacherId
        && grade.courseCode === courseCode && grade.semester === semester);
    },
    updateCourseGrade(userId, courseCode, semester, studentId, changes) {
      const teacher = teacherProfileByUser.get(userId);
      if (!teacher || !(teacherCourseAssignments.get(userId) ?? []).includes(courseCode)) return null;
      const grade = courseGrades.find((item) => item.courseCode === courseCode
        && item.semester === semester && item.studentId === studentId && item.teacherId === teacher.teacherId);
      if (!grade) return null;
      Object.assign(grade, changes, { updatedAt: new Date().toISOString(), manuallyEdited: true });
      this.createNotification(studentId, {
        type: 'grade', title: 'Course grade updated', message: `${courseCode} · New score: ${grade.totalScore}`
      });
      persist();
      return { ...grade };
    },
    updateCourseFeedback(userId, courseCode, semester, studentId, manualFeedback) {
      const teacher = teacherProfileByUser.get(userId);
      if (!teacher || !(teacherCourseAssignments.get(userId) ?? []).includes(courseCode)) return null;
      const grade = courseGrades.find((item) => item.courseCode === courseCode
        && item.semester === semester && item.studentId === studentId && item.teacherId === teacher.teacherId);
      if (!grade) return null;
      grade.manualFeedback = manualFeedback;
      this.createNotification(studentId, {
        type: 'feedback', title: 'New teacher feedback', message: `${courseCode} · Your teacher added feedback.`
      });
      persist();
      return { ...grade };
    },
    listAssignmentsForTeacher(userId) {
      return assignments.filter((assignment) => assignment.teacherId === userId).map((assignment) => ({ ...assignment }));
    },
    findAssignmentForTeacher(userId, assignmentId) {
      return assignments.find((assignment) => assignment.assignmentId === assignmentId
        && assignment.teacherId === userId) ?? null;
    },
    createAssignment(userId, assignment) {
      const created = {
        ...assignment,
        assignmentId: randomUUID(),
        teacherId: userId,
        closed: false,
        readBy: [],
        createdAt: new Date().toISOString()
      };
      assignments = [created, ...assignments];
      persist();
      return { ...created };
    },
    updateAssignment(userId, assignmentId, changes) {
      const assignment = this.findAssignmentForTeacher(userId, assignmentId);
      if (!assignment) return null;
      if (Object.keys(changes).some((key) => changes[key] !== assignment[key])) assignment.readBy = [];
      Object.assign(assignment, changes, { updatedAt: new Date().toISOString() });
      persist();
      return { ...assignment };
    },
    deleteAssignment(userId, assignmentId) {
      const index = assignments.findIndex((assignment) => assignment.assignmentId === assignmentId
        && assignment.teacherId === userId);
      if (index < 0) return false;
      assignments.splice(index, 1);
      persist();
      return true;
    },
    findSubmission(submissionId) {
      return submissions.find((submission) => submission.submissionId === submissionId) ?? null;
    },
    findSubmissionByHash(fileHash) {
      return submissions.find((submission) => submission.fileHash === fileHash) ?? null;
    },
    createSubmission(submission) {
      submissions = [{
        ...submission,
        submissionId: submission.submissionId ?? `SUB-${randomUUID()}`,
        submittedAt: submission.submittedAt ?? new Date().toISOString()
      }, ...submissions];
      if (submissions[0].score) {
        this.createNotification(submissions[0].userId, {
          type: 'grade', title: 'Submission analysis complete',
          message: `${submissions[0].fileName} · Score: ${submissions[0].score.totalScore}`
        });
      }
      persist();
      return submissions[0];
    },
    findSubmissionForTeacher(userId, submissionId) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      const teacherCourses = teacherCourseAssignments.get(userId) ?? [];
      const student = academicStudents.find((item) => item.studentId === submission.userId);
      if (!student || !student.courseCodes.some((courseCode) => teacherCourses.includes(courseCode))) return null;
      if (submission.assignmentId) {
        const assignment = assignments.find((item) => item.assignmentId === submission.assignmentId);
        if (!assignment || assignment.teacherId !== userId || !teacherCourses.includes(assignment.courseCode)) return null;
      } else if (!student.courseCodes.some((courseCode) => teacherCourses.includes(courseCode))) {
        return null;
      }
      return submission;
    },
    findSimilarSubmissions(userId, assignmentId, minimumSimilarity = 0.35) {
      const assignment = this.findAssignmentForTeacher(userId, assignmentId);
      if (!assignment) return null;
      const participants = submissions
        .filter((submission) => submission.assignmentId === assignmentId && submission.sourceCode)
        .map((submission) => ({
          submission,
          shingles: sourceShingles(submission.sourceCode),
          studentName: users.find((user) => user.userId === submission.userId)?.fullName ?? 'Unknown student'
        }));
      const pairs = [];
      for (let first = 0; first < participants.length; first += 1) {
        for (let second = first + 1; second < participants.length; second += 1) {
          if (participants[first].submission.userId === participants[second].submission.userId) continue;
          const similarity = jaccardSimilarity(participants[first].shingles, participants[second].shingles);
          if (similarity < minimumSimilarity) continue;
          pairs.push({
            first: {
              submissionId: participants[first].submission.submissionId,
              studentName: participants[first].studentName,
              fileName: participants[first].submission.fileName
            },
            second: {
              submissionId: participants[second].submission.submissionId,
              studentName: participants[second].studentName,
              fileName: participants[second].submission.fileName
            },
            similarity: Number((similarity * 100).toFixed(1))
          });
        }
      }
      return pairs.sort((first, second) => second.similarity - first.similarity);
    },
    listGradeAuditForSubmission(userId, submissionId) {
      if (!this.findSubmissionForTeacher(userId, submissionId)) return null;
      return gradeAuditLog.filter((entry) => entry.submissionId === submissionId).map((entry) => ({ ...entry }));
    },
    updateAnalysis(submissionId, analysis, score) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      submission.analysis = analysis;
      submission.score = { ...score, updatedAt: new Date().toISOString(), manuallyEdited: false };
      submission.status = analysis.syntaxValid ? 'Graded' : 'Needs Review';
      this.createNotification(submission.userId, {
        type: 'grade', title: 'Submission analysis complete', message: `${submission.fileName} · Score: ${submission.score.totalScore}`
      });
      persist();
      return submission;
    },
    updateGrade(userId, submissionId, grade, reason) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      const previous = submission.score ? { ...submission.score } : null;
      submission.score = {
        ...submission.score,
        ...grade,
        updatedAt: new Date().toISOString(),
        manuallyEdited: true
      };
      gradeAuditLog.unshift({
        submissionId,
        actorUserId: userId,
        changedAt: submission.score.updatedAt,
        reason,
        previous,
        next: { ...submission.score }
      });
      this.createNotification(submission.userId, {
        type: 'grade', title: 'Submission grade updated', message: `${submission.fileName} · New score: ${submission.score.totalScore}`
      });
      persist();
      return submission;
    },
    updateFeedback(submissionId, feedback) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      submission.manualFeedback = feedback;
      this.createNotification(submission.userId, {
        type: 'feedback', title: 'New teacher feedback', message: `${submission.fileName} · Your teacher added feedback.`
      });
      persist();
      return submission;
    },
    listRules() {
      return [...rules];
    },
    updateRule(ruleCode, changes) {
      const rule = rules.find((item) => item.ruleCode === ruleCode);
      if (!rule) return null;
      Object.assign(rule, changes);
      persist();
      return rule;
    },
    createClassReport(userId) {
      const teacherCourses = new Set(teacherCourseAssignments.get(userId) ?? []);
      const teacherSubmissions = this.listSubmissionsForTeacher(userId);
      const studentIds = new Set(academicStudents
        .filter((student) => student.courseCodes.some((courseCode) => teacherCourses.has(courseCode)))
        .map((student) => student.studentId));
      const students = users.filter((user) => user.role.toLowerCase() === 'student' && studentIds.has(user.userId));
      const reportRows = students.map((student) => {
        const studentSubmissions = teacherSubmissions.filter((submission) => submission.userId === student.userId);
        const graded = studentSubmissions.filter((submission) => Number.isFinite(submission.score?.totalScore));
        const average = graded.length
          ? graded.reduce((sum, submission) => sum + submission.score.totalScore, 0) / graded.length
          : null;
        return {
          userId: student.userId,
          fullName: student.fullName,
          submissionCount: studentSubmissions.length,
          gradedCount: graded.length,
          averageScore: average === null ? null : Number(average.toFixed(2)),
          scores: graded.map((submission) => ({
            submissionId: submission.submissionId,
            score: submission.score.totalScore,
            submittedAt: submission.submittedAt
          }))
        };
      });
      const allScores = reportRows.flatMap((row) => row.scores.map((item) => item.score));
      const violationCounts = new Map();
      for (const submission of teacherSubmissions) {
        for (const violation of submission.analysis?.violations ?? []) {
          const code = violation.ruleCode || violation.ruleName || 'Unknown';
          const current = violationCounts.get(code) ?? { ruleCode: code, count: 0 };
          current.count += 1;
          violationCounts.set(code, current);
        }
      }
      const teacherAssignments = assignments.filter((assignment) => assignment.teacherId === userId);
      const assignmentRosters = teacherAssignments.map((assignment) => {
        const roster = academicStudents.filter((student) => student.courseCodes.includes(assignment.courseCode));
        const submitted = roster.filter((student) => teacherSubmissions.some((submission) => submission.assignmentId === assignment.assignmentId
          && submission.userId === student.studentId));
        return {
          assignmentId: assignment.assignmentId,
          title: assignment.title,
          courseCode: assignment.courseCode,
          enrolledCount: roster.length,
          submittedCount: submitted.length,
          submissionRate: roster.length ? Number((submitted.length / roster.length * 100).toFixed(1)) : 0
        };
      });
      const distribution = [
        { range: '0-4.9', min: 0, max: 4.9 },
        { range: '5-5.9', min: 5, max: 5.9 },
        { range: '6-6.9', min: 6, max: 6.9 },
        { range: '7-7.9', min: 7, max: 7.9 },
        { range: '8-8.9', min: 8, max: 8.9 },
        { range: '9-10', min: 9, max: 10 }
      ].map(({ range, min, max }) => ({ range, count: allScores.filter((score) => score >= min && score <= max).length }));

      return {
        studentCount: students.length,
        submissionCount: teacherSubmissions.length,
        gradedSubmissionCount: allScores.length,
        classAverage: allScores.length
          ? Number((allScores.reduce((sum, score) => sum + score, 0) / allScores.length).toFixed(2))
          : null,
        scoreDistribution: distribution,
        mostCommonViolations: [...violationCounts.values()].sort((first, second) => second.count - first.count).slice(0, 5),
        assignmentRosters,
        students: reportRows
      };
    }
  };
}