package com.group1;

import com.group1.console.ConsoleDataStore;
import com.group1.model.AnalysisResult;
import com.group1.model.RuleDefinition;
import com.group1.model.RuleViolation;
import com.group1.model.Score;
import com.group1.model.Submission;
import com.group1.model.Users;
import com.group1.model.main.AnalysisOutcome;
import com.group1.model.main.Finding;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Main {
    private final Scanner scanner = new Scanner(System.in);
    private final ConsoleDataStore store = new ConsoleDataStore();
    private Users currentUser;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        String role = chooseRole();
        if (role == null) {
            System.out.println("Goodbye!");
            return;
        }
        currentUser = login(role);
        if (currentUser == null) {
            System.out.println("Goodbye!");
            return;
        }
        System.out.println("Login successful. Welcome, " + currentUser.username + "!");
        if ("Student".equalsIgnoreCase(role))
            runStudentMenu();
        else
            runTeacherMenu();
        System.out.println("Goodbye!");
    }

    private String chooseRole() {
        while (true) {
            System.out.println("\n=== Welcome to AITA ===");
            System.out.println("1. Student");
            System.out.println("2. Teacher");
            System.out.println("0. Exit");
            String choice = readLine("Choose your role: ").trim();
            if ("1".equals(choice))
                return "Student";
            if ("2".equals(choice))
                return "Teacher";
            if ("0".equals(choice))
                return null;
            System.out.println("Invalid option. Please choose 1, 2, or 0.");
        }
    }

    private Users login(String role) {
        List<Users> users = store.readUsers();
        if (users.isEmpty()) {
            System.out.println("Unable to load users from data/users.csv.");
            return null;
        }
        while (true) {
            System.out.println("\n=== " + role + " Login ===");
            String username = readLine("Username (0 to exit): ").trim();
            if ("0".equals(username))
                return null;
            String passwordHash = readLine("Password hash: ").trim();
            for (Users user : users) {
                if (username.equals(user.username) && passwordHash.equals(user.passwordHash)
                        && role.equalsIgnoreCase(user.role))
                    return user;
            }
            System.out.println("Invalid username, password hash, or role. Please try again.");
        }
    }

    private void runStudentMenu() {
        while (true) {
            System.out.println("\n=== Student Menu ===");
            System.out.println("1. Submit Assignment");
            System.out.println("2. Submit Source Code");
            System.out.println("3. View Processing Queue");
            System.out.println("4. Resubmit After Error");
            System.out.println("5. View Submission History");
            System.out.println("6. View My Grades");
            System.out.println("7. View Analysis Results");
            System.out.println("8. View AI Error Explanations");
            System.out.println("9. View Suggested Fixes");
            System.out.println("0. Log Out");
            switch (readInt("Choose an option: ", 0, 9)) {
                case 0:
                    return;
                case 1:
                    submitAssignment();
                    break;
                case 2:
                    submitSourceCode();
                    break;
                case 3:
                    viewProcessingQueue();
                    break;
                case 4:
                    resubmitAfterError();
                    break;
                case 5:
                    viewSubmissionHistory();
                    break;
                case 6:
                    viewMyGrades();
                    break;
                case 7:
                    viewAnalysisResults(false);
                    break;
                case 8:
                    viewAnalysisResults(true);
                    break;
                case 9:
                    viewSuggestedFixes();
                    break;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private void runTeacherMenu() {
        while (true) {
            System.out.println("\n=== Teacher Menu ===");
            System.out.println("1. Export Class Report (HTML / CSV)");
            System.out.println("2. Manage Class Submission List");
            System.out.println("3. View Summary Reports");
            System.out.println("4. Configure Rule Engine");
            System.out.println("5. Run Automatic Grading");
            System.out.println("6. Edit Grades");
            System.out.println("7. Provide Manual Feedback");
            System.out.println("8. Track Analysis Progress");
            System.out.println("0. Log Out");
            switch (readInt("Choose an option: ", 0, 8)) {
                case 0:
                    return;
                case 1:
                    exportClassReport();
                    break;
                case 2:
                    manageClassSubmissions();
                    break;
                case 3:
                    viewSummaryReports();
                    break;
                case 4:
                    configureRuleEngine();
                    break;
                case 5:
                    runAutomaticGrading();
                    break;
                case 6:
                    editGrade();
                    break;
                case 7:
                    provideManualFeedback();
                    break;
                case 8:
                    trackAnalysisProgress();
                    break;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private void submitAssignment() {
        String label = readRequired("Assignment name: ");
        Path source = promptForFile("Assignment file path (.zip, .pdf, or source file): ");
        if (source != null)
            createSubmission(source, label, false);
    }

    private void submitSourceCode() {
        Path source = promptForFile("Java source file path: ");
        if (source == null)
            return;
        if (!source.getFileName().toString().toLowerCase().endsWith(".java")) {
            System.out.println("Only .java source files can be analyzed.");
            return;
        }
        createSubmission(source, "SourceCode", true);
    }

    private void createSubmission(Path source, String label, boolean analyzeImmediately) {
        String id = store.nextSubmissionId();
        try {
            Submission submission = new Submission(id, currentUser.userId, copyToUploads(source, id, label),
                    sha256(source), analyzeImmediately ? "Queued" : "Submitted", timestamp());
            List<Submission> submissions = store.readSubmissions();
            submissions.add(submission);
            store.saveSubmissions(submissions);
            System.out.println("Submission saved. ID: " + id);
            System.out.println("SHA-256: " + submission.fileHash);
            if (analyzeImmediately)
                analyzeSubmission(submission);
        } catch (IOException | NoSuchAlgorithmException exception) {
            System.out.println("Unable to submit the file: " + exception.getMessage());
        }
    }

    private void resubmitAfterError() {
        List<Submission> eligible = new ArrayList<>();
        for (Submission item : store.readSubmissions()) {
            if (item.userId.equals(currentUser.userId) && ("Needs Review".equalsIgnoreCase(item.status)
                    || "Error".equalsIgnoreCase(item.status)))
                eligible.add(item);
        }
        if (eligible.isEmpty()) {
            System.out.println("There are no submissions that need resubmission.");
            return;
        }
        printSubmissions(eligible, false);
        Submission selected = findSubmission(eligible, readRequired("Submission ID to resubmit: "));
        if (selected == null) {
            System.out.println("Submission not found.");
            return;
        }
        Path source = promptForFile("Replacement Java source path: ");
        if (source == null)
            return;
        if (!source.getFileName().toString().toLowerCase().endsWith(".java")) {
            System.out.println("Only .java source files can be resubmitted for analysis.");
            return;
        }
        try {
            selected.fileUrl = copyToUploads(source, selected.submissionId, "resubmission");
            selected.fileHash = sha256(source);
            selected.status = "Queued";
            selected.submittedAt = timestamp();
            saveSubmissionUpdate(selected);
            analyzeSubmission(selected);
        } catch (IOException | NoSuchAlgorithmException exception) {
            System.out.println("Unable to resubmit the file: " + exception.getMessage());
        }
    }

    private void viewProcessingQueue() {
        List<Submission> queue = new ArrayList<>();
        for (Submission item : store.readSubmissions()) {
            if (item.userId.equals(currentUser.userId) && !"Graded".equalsIgnoreCase(item.status))
                queue.add(item);
        }
        System.out.println("\n=== Processing Queue ===");
        if (queue.isEmpty())
            System.out.println("No pending submissions.");
        else
            printSubmissions(queue, false);
    }

    private void viewSubmissionHistory() {
        List<Submission> own = submissionsForCurrentUser();
        System.out.println("\n=== Submission History ===");
        if (own.isEmpty())
            System.out.println("No submissions found.");
        else
            printSubmissions(own, false);
    }

    private void viewMyGrades() {
        Map<String, Submission> own = submissionMap(submissionsForCurrentUser());
        List<Score> scores = new ArrayList<>();
        for (Score score : store.readScores())
            if (own.containsKey(score.submissionId))
                scores.add(score);
        System.out.println("\n=== My Grades ===");
        if (scores.isEmpty()) {
            System.out.println("No grades are available for your submissions yet.");
            return;
        }
        for (Score score : scores) {
            System.out.printf("%s | %s | Score: %.1f | Deduction: %.1f | %s%n", score.submissionId,
                    fileName(own.get(score.submissionId).fileUrl), score.totalScore, score.deduction,
                    score.gradingNotes);
        }
    }

    private void viewAnalysisResults(boolean explanationsOnly) {
        Map<String, Submission> own = submissionMap(submissionsForCurrentUser());
        boolean found = false;
        System.out.println(explanationsOnly ? "\n=== AI Error Explanations ===" : "\n=== Analysis Results ===");
        for (AnalysisResult analysis : store.readAnalysisResults()) {
            if (!own.containsKey(analysis.submissionId))
                continue;
            found = true;
            if (!explanationsOnly) {
                System.out.printf("%s | %s | %d ms%n", analysis.submissionId,
                        fileName(own.get(analysis.submissionId).fileUrl), analysis.executionTimeMs);
                System.out.println("  AST: " + analysis.astJson);
            }
            System.out.println("  " + analysis.aiFeedback);
            printManualFeedback(analysis.submissionId);
            printViolations(analysis);
        }
        if (!found)
            System.out.println("No analysis results are available yet.");
    }

    private void viewSuggestedFixes() {
        Map<String, Submission> own = submissionMap(submissionsForCurrentUser());
        Map<Integer, AnalysisResult> analyses = new HashMap<>();
        for (AnalysisResult analysis : store.readAnalysisResults())
            analyses.put(analysis.analysisId, analysis);
        System.out.println("\n=== Suggested Fixes ===");
        boolean found = false;
        for (RuleViolation violation : store.readViolations()) {
            AnalysisResult analysis = analyses.get(violation.analysisId);
            if (analysis == null || !own.containsKey(analysis.submissionId))
                continue;
            found = true;
            RuleDefinition rule = findRule(violation.ruleId);
            System.out.printf("%s | %s:%d | %s | %s%n", analysis.submissionId, violation.fileName,
                    violation.lineNumber, rule == null ? "Rule " + violation.ruleId : rule.ruleName,
                    violation.suggestedFix);
        }
        if (!found)
            System.out.println("No suggested fixes are available.");
    }

    private void manageClassSubmissions() {
        List<Submission> submissions = store.readSubmissions();
        System.out.println("\n=== Class Submission List ===");
        if (submissions.isEmpty())
            System.out.println("No submissions found.");
        else
            printSubmissions(submissions, true);
    }

    private void viewSummaryReports() {
        List<Users> students = students();
        List<Submission> submissions = store.readSubmissions();
        List<Score> scores = store.readScores();
        int totalGraded = 0;
        double totalScore = 0;
        System.out.println("\n=== Class Summary Report ===");
        for (Users student : students) {
            int submissionCount = 0;
            int graded = 0;
            double studentScore = 0;
            for (Submission submission : submissions)
                if (submission.userId.equals(student.userId))
                    submissionCount++;
            for (Score score : scores) {
                Submission submission = findSubmission(submissions, score.submissionId);
                if (submission != null && submission.userId.equals(student.userId)) {
                    studentScore += score.totalScore;
                    graded++;
                }
            }
            totalGraded += graded;
            totalScore += studentScore;
            String average = graded == 0 ? "-" : String.format("%.2f", studentScore / graded);
            System.out.printf("%-24s | %-7s | Submissions: %3d | Graded: %3d | Average: %s%n",
                    student.fullName, student.userId, submissionCount, graded, average);
        }
        System.out.printf("Class total: %d students | %d submissions | %d graded | Average: %s%n",
                students.size(), submissions.size(), totalGraded,
                totalGraded == 0 ? "-" : String.format("%.2f", totalScore / totalGraded));
    }

    private void exportClassReport() {
        try {
            Path reports = store.getDataDirectory().resolve("reports");
            Files.createDirectories(reports);
            List<String> csvRows = new ArrayList<>();
            StringBuilder html = new StringBuilder(
                    "<!doctype html><html><head><meta charset=\"utf-8\"><title>Class Summary</title>")
                    .append("<style>body{font:14px Arial;margin:32px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #bbb;padding:8px;text-align:left}th{background:#e8f0e9}</style>")
                    .append("</head><body><h1>Submission Portal - AITA</h1><h2>Class Summary Report</h2><table><thead><tr><th>Student</th><th>User ID</th><th>Submissions</th><th>Graded</th><th>Average Score</th></tr></thead><tbody>");
            csvRows.add("Student,User ID,Submissions,Graded,Average Score");
            List<Users> students = students();
            List<Submission> submissions = store.readSubmissions();
            List<Score> scores = store.readScores();
            for (Users student : students) {
                int submissionCount = 0;
                int graded = 0;
                double total = 0;
                for (Submission submission : submissions)
                    if (submission.userId.equals(student.userId))
                        submissionCount++;
                for (Score score : scores) {
                    Submission submission = findSubmission(submissions, score.submissionId);
                    if (submission != null && submission.userId.equals(student.userId)) {
                        total += score.totalScore;
                        graded++;
                    }
                }
                String average = graded == 0 ? "" : String.format("%.2f", total / graded);
                csvRows.add(csv(student.fullName) + "," + csv(student.userId) + "," + submissionCount + "," + graded
                        + "," + average);
                html.append("<tr><td>").append(escapeHtml(student.fullName)).append("</td><td>")
                        .append(escapeHtml(student.userId)).append("</td><td>").append(submissionCount)
                        .append("</td><td>").append(graded).append("</td><td>")
                        .append(average.isEmpty() ? "-" : average).append("</td></tr>");
            }
            html.append("</tbody></table><p>Use Print &gt; Save as PDF in your browser.</p></body></html>");
            Files.write(reports.resolve("class-summary.csv"), csvRows, StandardCharsets.UTF_8);
            Files.write(reports.resolve("class-summary.html"), Arrays.asList(html.toString()), StandardCharsets.UTF_8);
            System.out.println("Report exported to " + reports.toAbsolutePath());
        } catch (IOException exception) {
            System.out.println("Unable to export report: " + exception.getMessage());
        }
    }

    private void configureRuleEngine() {
        List<RuleDefinition> rules = store.readRules();
        printRules(rules);
        int id = readInt("Rule ID to configure (0 to cancel): ", 0, Integer.MAX_VALUE);
        if (id == 0)
            return;
        RuleDefinition selected = null;
        for (RuleDefinition rule : rules)
            if (rule.ruleId == id)
                selected = rule;
        if (selected == null) {
            System.out.println("Rule not found.");
            return;
        }
        selected.penaltyPoints = readFloat("New penalty points (0-10): ", 0, 10);
        String severity = readLine("New severity (Enter to keep current): ").trim();
        if (!severity.isEmpty())
            selected.severity = severity;
        String description = readLine("New description (Enter to keep current): ").trim();
        if (!description.isEmpty())
            selected.description = description;
        saveRules(rules);
    }

    private void runAutomaticGrading() {
        List<Submission> queue = new ArrayList<>();
        for (Submission submission : store.readSubmissions()) {
            if (isLocalJavaFile(submission) && !"Graded".equalsIgnoreCase(submission.status))
                queue.add(submission);
        }
        if (queue.isEmpty()) {
            System.out.println("No queued local Java source files. New source code is analyzed on submission.");
            return;
        }
        int completed = 0;
        for (Submission submission : queue)
            if (analyzeSubmission(submission))
                completed++;
        System.out.printf("Automatic grading completed for %d of %d submissions.%n", completed, queue.size());
    }

    private void editGrade() {
        List<Submission> submissions = store.readSubmissions();
        printSubmissions(submissions, true);
        String id = readRequired("Submission ID to grade: ");
        if (findSubmission(submissions, id) == null) {
            System.out.println("Submission not found.");
            return;
        }
        float total = readFloat("Score (0-10): ", 0, 10);
        float deduction = readFloat("Deduction (0-10): ", 0, 10);
        String notes = readRequired("Grading notes: ");
        List<Score> scores = store.readScores();
        Score score = findScore(scores, id);
        if (score == null)
            scores.add(new Score(store.nextScoreId(), id, total, deduction, notes, timestamp()));
        else {
            score.totalScore = total;
            score.deduction = deduction;
            score.gradingNotes = notes;
            score.updatedAt = timestamp();
        }
        try {
            store.saveScores(scores);
            System.out.println("Grade saved for " + id + ".");
        } catch (IOException exception) {
            System.out.println("Unable to save grade: " + exception.getMessage());
        }
    }

    private void provideManualFeedback() {
        List<Submission> submissions = store.readSubmissions();
        printSubmissions(submissions, true);
        String id = readRequired("Submission ID for feedback: ");
        if (findSubmission(submissions, id) == null) {
            System.out.println("Submission not found.");
            return;
        }
        List<String[]> feedback = store.readManualFeedback();
        int nextId = 1;
        for (String[] row : feedback) {
            try {
                nextId = Math.max(nextId, Integer.parseInt(row[0]) + 1);
            } catch (RuntimeException ignored) {
            }
        }
        feedback.add(new String[] { String.valueOf(nextId), id, currentUser.userId,
                readRequired("Manual feedback: "), timestamp() });
        try {
            store.saveManualFeedback(feedback);
            System.out.println("Manual feedback saved separately from the numeric grade.");
        } catch (IOException exception) {
            System.out.println("Unable to save feedback: " + exception.getMessage());
        }
    }

    private void trackAnalysisProgress() {
        Map<String, Integer> counts = new HashMap<>();
        for (Submission submission : store.readSubmissions()) {
            String status = submission.status == null ? "Unknown" : submission.status;
            counts.put(status, counts.containsKey(status) ? counts.get(status) + 1 : 1);
        }
        System.out.println("\n=== Analysis Progress ===");
        for (Map.Entry<String, Integer> entry : counts.entrySet())
            System.out.printf("%-20s %d%n", entry.getKey(), entry.getValue());
    }

    private boolean analyzeSubmission(Submission submission) {
        Path source = localSourcePath(submission);
        if (source == null || !Files.isRegularFile(source)) {
            System.out.println(submission.submissionId + ": source file is not available locally.");
            return false;
        }
        long started = System.currentTimeMillis();
        try {
            AnalysisOutcome outcome = analyzeJava(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
            int analysisId = store.nextAnalysisId();
            List<AnalysisResult> analyses = store.readAnalysisResults();
            analyses.add(new AnalysisResult(analysisId, submission.submissionId,
                    "{\"classes\":" + outcome.classCount + ",\"methods\":" + outcome.methodCount
                            + ",\"syntaxValid\":" + outcome.syntaxValid + "}",
                    outcome.feedback, (int) (System.currentTimeMillis() - started)));
            store.saveAnalysisResults(analyses);
            List<RuleViolation> violations = store.readViolations();
            int nextViolationId = store.nextViolationId();
            for (Finding finding : outcome.findings) {
                violations.add(new RuleViolation(nextViolationId++, analysisId, finding.rule.ruleId,
                        fileName(submission.fileUrl), finding.line, finding.suggestion));
            }
            store.saveViolations(violations);
            List<Score> scores = store.readScores();
            Score score = findScore(scores, submission.submissionId);
            float deduction = outcome.syntaxValid ? outcome.deduction : 10.0f;
            float total = Math.max(0.0f, 10.0f - deduction);
            if (score == null)
                scores.add(new Score(store.nextScoreId(), submission.submissionId, total,
                        deduction, "Rule Engine automatic grade", timestamp()));
            else {
                score.totalScore = total;
                score.deduction = deduction;
                score.gradingNotes = outcome.syntaxValid ? "Rule Engine automatic grade"
                        : "Source requires syntax review";
                score.updatedAt = timestamp();
            }
            store.saveScores(scores);
            submission.status = outcome.syntaxValid ? "Graded" : "Needs Review";
            saveSubmissionUpdate(submission);
            System.out.printf("%s: %s | Score %.1f / 10 | %d rule findings%n",
                    submission.submissionId, submission.status, total, outcome.findings.size());
            return true;
        } catch (IOException exception) {
            System.out.println("Unable to analyze " + submission.submissionId + ": " + exception.getMessage());
            return false;
        }
    }

    private AnalysisOutcome analyzeJava(String source) {
        AnalysisOutcome result = new AnalysisOutcome();
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (ParseProblemException exception) {
            result.syntaxValid = false;
            String message = exception.getMessage();
            result.feedback = "Java syntax could not be parsed. "
                    + (message == null ? "Check the submitted source." : message.split("\\n", 2)[0]);
            return result;
        }
        result.syntaxValid = true;
        List<RuleDefinition> rules = store.readRules();
        result.classCount = unit.findAll(ClassOrInterfaceDeclaration.class).size();
        result.methodCount = unit.findAll(MethodDeclaration.class).size();
        Set<String> seen = new HashSet<>();
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            String name = type.getNameAsString();
            if (!name.isEmpty() && Character.isLowerCase(name.charAt(0)))
                addFinding(result, rules, seen, "CS", type, "Use an uppercase initial letter for Java class names.");
        }
        for (ReturnStmt statement : unit.findAll(ReturnStmt.class)) {
            if (statement.getExpression().isPresent() && statement.getExpression().get() instanceof NullLiteralExpr)
                addFinding(result, rules, seen, "NPE", statement, "Avoid returning null without checking the result.");
        }
        List<Node> loops = new ArrayList<>();
        loops.addAll(unit.findAll(ForStmt.class));
        loops.addAll(unit.findAll(ForEachStmt.class));
        loops.addAll(unit.findAll(WhileStmt.class));
        loops.addAll(unit.findAll(DoStmt.class));
        for (Node loop : loops) {
            for (ArrayCreationExpr array : loop.findAll(ArrayCreationExpr.class))
                addFinding(result, rules, seen, "OOM", array, "Review repeated array allocation inside a loop.");
        }
        for (Finding finding : result.findings)
            result.deduction += finding.rule.penaltyPoints;
        result.deduction = Math.min(10, result.deduction);
        if (result.findings.isEmpty())
            result.feedback = "AST parsing completed. No configured rule violations were found.";
        else {
            StringBuilder text = new StringBuilder("Rule Engine found ").append(result.findings.size())
                    .append(" issue(s): ");
            for (int index = 0; index < result.findings.size(); index++) {
                if (index > 0)
                    text.append("; ");
                Finding finding = result.findings.get(index);
                text.append(finding.rule.ruleCode).append(" at line ").append(finding.line);
            }
            result.feedback = text.toString();
        }
        return result;
    }

    private void addFinding(AnalysisOutcome outcome, List<RuleDefinition> rules, Set<String> seen,
            String code, Node node, String suggestion) {
        RuleDefinition rule = findRule(rules, code);
        if (rule == null)
            return;
        int line = node.getBegin().isPresent() ? node.getBegin().get().line : 1;
        if (seen.add(rule.ruleId + ":" + line))
            outcome.findings.add(new Finding(rule, line, suggestion));
    }

    private void printViolations(AnalysisResult analysis) {
        for (RuleViolation violation : store.readViolations()) {
            if (violation.analysisId != analysis.analysisId)
                continue;
            RuleDefinition rule = findRule(violation.ruleId);
            System.out.printf("    %s line %d: %s%n", rule == null ? "Rule " + violation.ruleId : rule.ruleName,
                    violation.lineNumber, violation.suggestedFix);
        }
    }

    private void printManualFeedback(String id) {
        for (String[] row : store.readManualFeedback())
            if (row.length >= 5 && row[1].equals(id))
                System.out.println("  Teacher feedback: " + row[3]);
    }

    private void printSubmissions(List<Submission> submissions, boolean includeStudent) {
        Map<String, Users> users = new HashMap<>();
        for (Users user : store.readUsers())
            users.put(user.userId, user);
        List<Score> scores = store.readScores();
        for (Submission submission : submissions) {
            Score score = findScore(scores, submission.submissionId);
            Users student = users.get(submission.userId);
            String name = includeStudent ? (student == null ? submission.userId : student.fullName) + " | " : "";
            System.out.printf("%s%s | %s | %s | %s%n", name, submission.submissionId,
                    fileName(submission.fileUrl), submission.status,
                    score == null ? "Not graded" : String.format("Score %.1f", score.totalScore));
        }
    }

    private void printRules(List<RuleDefinition> rules) {
        for (RuleDefinition rule : rules)
            System.out.printf("%d | %s | %s | %s | Penalty %.1f | %s%n", rule.ruleId, rule.ruleCode,
                    rule.ruleName, rule.severity, rule.penaltyPoints, rule.description);
    }

    private List<Submission> submissionsForCurrentUser() {
        List<Submission> result = new ArrayList<>();
        for (Submission submission : store.readSubmissions())
            if (submission.userId.equals(currentUser.userId))
                result.add(submission);
        return result;
    }

    private List<Users> students() {
        List<Users> result = new ArrayList<>();
        for (Users user : store.readUsers())
            if ("Student".equalsIgnoreCase(user.role))
                result.add(user);
        return result;
    }

    private Map<String, Submission> submissionMap(List<Submission> submissions) {
        Map<String, Submission> result = new HashMap<>();
        for (Submission submission : submissions)
            result.put(submission.submissionId, submission);
        return result;
    }

    private Submission findSubmission(List<Submission> submissions, String id) {
        for (Submission submission : submissions)
            if (submission.submissionId.equalsIgnoreCase(id))
                return submission;
        return null;
    }

    private Score findScore(List<Score> scores, String id) {
        for (Score score : scores)
            if (score.submissionId.equalsIgnoreCase(id))
                return score;
        return null;
    }

    private RuleDefinition findRule(int id) {
        for (RuleDefinition rule : store.readRules())
            if (rule.ruleId == id)
                return rule;
        return null;
    }

    private RuleDefinition findRule(List<RuleDefinition> rules, String code) {
        for (RuleDefinition rule : rules)
            if (rule.ruleCode.equalsIgnoreCase(code))
                return rule;
        return null;
    }

    private boolean isLocalJavaFile(Submission item) {
        return item.fileUrl != null && item.fileUrl.toLowerCase().endsWith(".java") && localSourcePath(item) != null;
    }

    private Path localSourcePath(Submission item) {
        if (item.fileUrl == null || item.fileUrl.startsWith("http://") || item.fileUrl.startsWith("https://"))
            return null;
        Path path = Paths.get(item.fileUrl);
        if (!path.isAbsolute())
            path = store.getDataDirectory().resolve(path);
        path = path.normalize().toAbsolutePath();
        return path.startsWith(store.getDataDirectory()) ? path : null;
    }

    private void saveSubmissionUpdate(Submission updated) throws IOException {
        List<Submission> submissions = store.readSubmissions();
        for (int index = 0; index < submissions.size(); index++) {
            if (submissions.get(index).submissionId.equals(updated.submissionId)) {
                submissions.set(index, updated);
                store.saveSubmissions(submissions);
                return;
            }
        }
        submissions.add(updated);
        store.saveSubmissions(submissions);
    }

    private String copyToUploads(Path source, String id, String label) throws IOException {
        Path uploads = store.getDataDirectory().resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploads);
        String name = source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        String safeLabel = label.replaceAll("[^A-Za-z0-9_-]", "_");
        Path target = uploads.resolve(id + "_" + safeLabel + "_" + name).normalize();
        if (!target.startsWith(uploads))
            throw new IOException("Invalid upload filename.");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return store.getDataDirectory().relativize(target).toString().replace('\\', '/');
    }

    private Path promptForFile(String prompt) {
        Path path = Paths.get(readRequired(prompt)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            System.out.println("File not found: " + path);
            return null;
        }
        return path;
    }

    private String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1)
                digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest())
            result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            try {
                int value = Integer.parseInt(readLine(prompt).trim());
                if (value >= min && value <= max)
                    return value;
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Enter a number from %d to %d.%n", min, max);
        }
    }

    private float readFloat(String prompt, float min, float max) {
        while (true) {
            try {
                float value = Float.parseFloat(readLine(prompt).trim());
                if (value >= min && value <= max)
                    return value;
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Enter a number from %.1f to %.1f.%n", min, max);
        }
    }

    private String readRequired(String prompt) {
        while (true) {
            String value = readLine(prompt).trim();
            if (!value.isEmpty())
                return value;
            System.out.println("This value is required.");
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private String fileName(String path) {
        if (path == null)
            return "";
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private String escapeHtml(String value) {
        return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void saveRules(List<RuleDefinition> rules) {
        try {
            store.saveRules(rules);
            System.out.println("Rule updated. Future grading runs will use the new settings.");
        } catch (IOException exception) {
            System.out.println("Unable to save rules: " + exception.getMessage());
        }
    }

}
