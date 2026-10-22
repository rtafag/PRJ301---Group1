package com.group1.util;

import com.group1.model.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

public class DatabaseSeeder {

    public static void main(String[] args) {
        seed();
    }

    public static void seed() {
        System.out.println("=== STARTING DATABASE SEEDING FROM CSV ===");
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("Connected to SQL Server database: " + conn.getCatalog());

            // 1. Drop existing tables if present to fix INT vs VARCHAR types
            dropAndRecreateTables(conn);

            // 2. Seed Users
            seedUsers(conn);

            // 3. Seed RuleDefinitions
            seedRules(conn);

            // 4. Seed Submissions
            seedSubmissions(conn);

            // 5. Seed Scores
            seedScores(conn);

            // 6. Seed AnalysisResults
            seedAnalysisResults(conn);

            // 7. Seed RuleViolations
            seedRuleViolations(conn);

            // 8. Re-add foreign key constraints
            addForeignKeys(conn);

            System.out.println("=== ALL CSV DATA IMPORTED TO DATABASE SUCCESSFULLY! ===");
        } catch (Exception e) {
            System.err.println("Database seeding failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void dropAndRecreateTables(Connection conn) throws Exception {
        System.out.println("Dropping old empty tables and recreating with correct types...");
        try (Statement s = conn.createStatement()) {
            // Drop foreign keys if exist
            String[] fks = {
                "ALTER TABLE RuleViolation DROP CONSTRAINT IF EXISTS FK_Violation_Rule",
                "ALTER TABLE RuleViolation DROP CONSTRAINT IF EXISTS FK_Violation_Analysis",
                "ALTER TABLE AnalysisResult DROP CONSTRAINT IF EXISTS FK_Analysis_Submission",
                "ALTER TABLE Score DROP CONSTRAINT IF EXISTS FK_Score_Submission",
                "ALTER TABLE Submission DROP CONSTRAINT IF EXISTS FK_Submission_Users"
            };
            for (String fk : fks) {
                try { s.execute(fk); } catch (Exception ignored) {}
            }

            // Drop tables
            s.execute("DROP TABLE IF EXISTS RuleViolation");
            s.execute("DROP TABLE IF EXISTS AnalysisResult");
            s.execute("DROP TABLE IF EXISTS Score");
            s.execute("DROP TABLE IF EXISTS Submission");
            s.execute("DROP TABLE IF EXISTS RuleDefinition");
            s.execute("DROP TABLE IF EXISTS Users");

            // Recreate tables with correct schemas matching models
            s.execute("CREATE TABLE Users (" +
                    "user_id VARCHAR(50) PRIMARY KEY, " +
                    "username VARCHAR(100), " +
                    "password_hash VARCHAR(255), " +
                    "full_name NVARCHAR(100), " +
                    "email VARCHAR(100), " +
                    "role VARCHAR(50), " +
                    "created_at DATETIME)");

            s.execute("CREATE TABLE RuleDefinition (" +
                    "rule_id INT PRIMARY KEY, " +
                    "rule_code VARCHAR(50), " +
                    "rule_name NVARCHAR(200), " +
                    "severity VARCHAR(50), " +
                    "penalty_points FLOAT, " +
                    "description NVARCHAR(MAX))");

            s.execute("CREATE TABLE Submission (" +
                    "submission_id VARCHAR(50) PRIMARY KEY, " +
                    "user_id VARCHAR(50), " +
                    "file_url VARCHAR(MAX), " +
                    "file_hash VARCHAR(100), " +
                    "status VARCHAR(50), " +
                    "submitted_at DATETIME)");

            s.execute("CREATE TABLE Score (" +
                    "score_id INT PRIMARY KEY, " +
                    "submission_id VARCHAR(50), " +
                    "total_score FLOAT, " +
                    "deduction FLOAT, " +
                    "grading_notes NVARCHAR(MAX), " +
                    "updated_at DATETIME)");

            s.execute("CREATE TABLE AnalysisResult (" +
                    "analysis_id INT PRIMARY KEY, " +
                    "submission_id VARCHAR(50), " +
                    "ast_json NVARCHAR(MAX), " +
                    "ai_feedback NVARCHAR(MAX), " +
                    "execution_time_ms INT)");

            s.execute("CREATE TABLE RuleViolation (" +
                    "violation_id INT PRIMARY KEY, " +
                    "analysis_id INT, " +
                    "rule_id INT, " +
                    "file_name VARCHAR(200), " +
                    "line_number INT, " +
                    "suggested_fix NVARCHAR(MAX))");
        }
        System.out.println("Tables recreated successfully.");
    }

    private static void seedUsers(Connection conn) throws Exception {
        List<Users> list = CSVHelper.readUsers("data/users.csv");
        String sql = "INSERT INTO Users (user_id, username, password_hash, full_name, email, role, created_at) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Users u : list) {
                ps.setString(1, u.userId);
                ps.setString(2, u.username);
                ps.setString(3, u.passwordHash);
                ps.setString(4, u.fullName);
                ps.setString(5, u.email);
                ps.setString(6, u.role);
                try {
                    ps.setString(7, u.createdAt);
                } catch (Exception e) {
                    ps.setString(7, null);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
        System.out.println("-> Imported " + list.size() + " users from data/users.csv");
    }

    private static void seedRules(Connection conn) throws Exception {
        List<RuleDefinition> list = CSVHelper.readRuleDefinitions("data/rule_definition.csv");
        String sql = "INSERT INTO RuleDefinition (rule_id, rule_code, rule_name, severity, penalty_points, description) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (RuleDefinition r : list) {
                ps.setInt(1, r.ruleId);
                ps.setString(2, r.ruleCode);
                ps.setString(3, r.ruleName);
                ps.setString(4, r.severity);
                ps.setFloat(5, r.penaltyPoints);
                ps.setString(6, r.description);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        System.out.println("-> Imported " + list.size() + " rules from data/rule_definition.csv");
    }

    private static void seedSubmissions(Connection conn) throws Exception {
        List<Submission> list = CSVHelper.readSubmissions("data/submission.csv");
        String sql = "INSERT INTO Submission (submission_id, user_id, file_url, file_hash, status, submitted_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Submission s : list) {
                ps.setString(1, s.submissionId);
                ps.setString(2, s.userId);
                ps.setString(3, s.fileUrl);
                ps.setString(4, s.fileHash);
                ps.setString(5, s.status);
                ps.setString(6, s.submittedAt);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        System.out.println("-> Imported " + list.size() + " submissions from data/submission.csv");
    }

    private static void seedScores(Connection conn) throws Exception {
        List<Score> list = CSVHelper.readScores("data/score.csv");
        String sql = "INSERT INTO Score (score_id, submission_id, total_score, deduction, grading_notes, updated_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Score sc : list) {
                ps.setInt(1, sc.scoreId);
                ps.setString(2, sc.submissionId);
                ps.setFloat(3, sc.totalScore);
                ps.setFloat(4, sc.deduction);
                ps.setString(5, sc.gradingNotes);
                ps.setString(6, sc.updatedAt);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        System.out.println("-> Imported " + list.size() + " scores from data/score.csv");
    }

    private static void seedAnalysisResults(Connection conn) throws Exception {
        List<AnalysisResult> list = CSVHelper.readAnalysisResults("data/analysis_result.csv");
        String sql = "INSERT INTO AnalysisResult (analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (AnalysisResult ar : list) {
                ps.setInt(1, ar.analysisId);
                ps.setString(2, ar.submissionId);
                ps.setString(3, ar.astJson);
                ps.setString(4, ar.aiFeedback);
                ps.setInt(5, ar.executionTimeMs);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        System.out.println("-> Imported " + list.size() + " analysis results from data/analysis_result.csv");
    }

    private static void seedRuleViolations(Connection conn) throws Exception {
        List<RuleViolation> list = CSVHelper.readRuleViolations("data/rule_violation.csv");
        String sql = "INSERT INTO RuleViolation (violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (RuleViolation rv : list) {
                ps.setInt(1, rv.violationId);
                ps.setInt(2, rv.analysisId);
                ps.setInt(3, rv.ruleId);
                ps.setString(4, rv.fileName);
                ps.setInt(5, rv.lineNumber);
                ps.setString(6, rv.suggestedFix);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        System.out.println("-> Imported " + list.size() + " rule violations from data/rule_violation.csv");
    }

    private static void addForeignKeys(Connection conn) throws Exception {
        System.out.println("Adding foreign key constraints...");
        try (Statement s = conn.createStatement()) {
            try {
                s.execute("ALTER TABLE Submission ADD CONSTRAINT FK_Submission_Users FOREIGN KEY (user_id) REFERENCES Users(user_id)");
            } catch (Exception e) {
                System.out.println("Note: FK_Submission_Users skipped: " + e.getMessage());
            }
            try {
                s.execute("ALTER TABLE Score ADD CONSTRAINT FK_Score_Submission FOREIGN KEY (submission_id) REFERENCES Submission(submission_id)");
            } catch (Exception e) {
                System.out.println("Note: FK_Score_Submission skipped: " + e.getMessage());
            }
            try {
                s.execute("ALTER TABLE AnalysisResult ADD CONSTRAINT FK_Analysis_Submission FOREIGN KEY (submission_id) REFERENCES Submission(submission_id)");
            } catch (Exception e) {
                System.out.println("Note: FK_Analysis_Submission skipped: " + e.getMessage());
            }
            try {
                s.execute("ALTER TABLE RuleViolation ADD CONSTRAINT FK_Violation_Analysis FOREIGN KEY (analysis_id) REFERENCES AnalysisResult(analysis_id)");
            } catch (Exception e) {
                System.out.println("Note: FK_Violation_Analysis skipped: " + e.getMessage());
            }
            try {
                s.execute("ALTER TABLE RuleViolation ADD CONSTRAINT FK_Violation_Rule FOREIGN KEY (rule_id) REFERENCES RuleDefinition(rule_id)");
            } catch (Exception e) {
                System.out.println("Note: FK_Violation_Rule skipped: " + e.getMessage());
            }
        }
    }
}
