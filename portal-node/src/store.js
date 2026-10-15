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

export function createStore() {
  const users = readCsv('users.csv').map((row) => ({
    userId: row.userId,
    username: row.username,
    passwordHash: row.passwordHash,
    fullName: row.fullName,
    email: row.email,
    role: row.role
  }));
  const teacherUsers = users.filter((user) => user.role.toLowerCase() === 'teacher');
  const studentUsers = users.filter((user) => user.role.toLowerCase() === 'student');
  const academicStudents = readCsv('students.csv').map((row, index) => ({
    studentId: studentUsers[index]?.userId ?? row.UserID,
    sourceStudentId: row.UserID,
    fullName: row.Name,
    gpa: Number(row.GPA),
    courseCodes: String(row.CourseCode ?? '').split('|').filter(Boolean),
    status: 'active'
  }));
  const academicTeachers = readCsv('teachers.csv').map((row, index) => ({
    ...row,
    teacherId: teacherUsers[index]?.userId ?? row.UserID,
    sourceTeacherId: row.UserID,
    courseCodes: String(row.CourseCode ?? '').split('|').filter(Boolean)
  }));
  const teacherProfileByUser = new Map(academicTeachers.map((teacher) => [teacher.teacherId, teacher]));
  const teacherCourseAssignments = new Map(academicTeachers.map((teacher) => [teacher.teacherId, teacher.courseCodes]));
  const teachersByCourse = new Map();
  for (const teacher of academicTeachers) {
    for (const courseCode of teacher.courseCodes) {
      const teachers = teachersByCourse.get(courseCode) ?? [];
      teachers.push(teacher.teacherId);
      teachersByCourse.set(courseCode, teachers);
    }
  }
  const courseCodes = [...new Set([
    ...academicStudents.flatMap((student) => student.courseCodes),
    ...academicTeachers.flatMap((teacher) => String(teacher.CourseCode ?? '').split('|').filter(Boolean))
  ])].sort();
  const studentIdBySourceId = new Map(academicStudents.map((student) => [student.sourceStudentId, student.studentId]));
  const teacherIdBySourceId = new Map(academicTeachers.map((teacher) => [teacher.sourceTeacherId, teacher.teacherId]));
  const legacyTeacherIdByUserId = new Map(teacherUsers.slice(0, 10).map((user, index) => [
    `U${String(index + 1).padStart(4, '0')}`,
    user.userId
  ]));
  const courseTeacherByStudent = new Map();
  for (const courseCode of courseCodes) {
    const teachers = teachersByCourse.get(courseCode) ?? [];
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
  const runtime = readRuntimeData();
  let submissions = Array.isArray(runtime?.submissions) ? runtime.submissions : createSeedSubmissions();
  let rules = Array.isArray(runtime?.rules) ? runtime.rules : csvRules;
  let courseGrades = Array.isArray(runtime?.courseGrades) ? runtime.courseGrades : createCourseGrades();
  let assignments = Array.isArray(runtime?.assignments) ? runtime.assignments : [];
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

  function persist() {
    fs.mkdirSync(runtimeDirectory, { recursive: true });
    const temporaryFile = `${runtimeFile}.tmp`;
    fs.writeFileSync(temporaryFile, JSON.stringify({ submissions, rules, courseGrades, assignments }, null, 2), 'utf8');
    fs.renameSync(temporaryFile, runtimeFile);
  }

  return {
    users,
    findUser(username) {
      return users.find((user) => user.username === username) ?? null;
    },
    listSubmissions() {
      return [...submissions];
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
      return (teacherCourseAssignments.get(userId) ?? [])
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
      persist();
      return submissions[0];
    },
    updateGrade(submissionId, grade) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      submission.score = {
        ...submission.score,
        ...grade,
        updatedAt: new Date().toISOString(),
        manuallyEdited: true
      };
      persist();
      return submission;
    },
    updateFeedback(submissionId, feedback) {
      const submission = this.findSubmission(submissionId);
      if (!submission) return null;
      submission.manualFeedback = feedback;
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
    createClassReport() {
      const students = users.filter((user) => user.role.toLowerCase() === 'student');
      const reportRows = students.map((student) => {
        const studentSubmissions = submissions.filter((submission) => submission.userId === student.userId);
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

      return {
        studentCount: students.length,
        submissionCount: submissions.length,
        gradedSubmissionCount: allScores.length,
        classAverage: allScores.length
          ? Number((allScores.reduce((sum, score) => sum + score, 0) / allScores.length).toFixed(2))
          : null,
        students: reportRows
      };
    }
  };
}