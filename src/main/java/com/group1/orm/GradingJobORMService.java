package com.group1.orm;

import com.group1.model.AnalysisResult;
import com.group1.model.RuleViolation;
import com.group1.model.Score;
import com.group1.model.Submission;
import com.group1.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA/ORM-oriented Service for managing the automated Grading Job Queue.
 * Ensures strict transactional consistency and synchronization across
 * Submission, AnalysisResult, Score, and RuleViolation tables.
 */
public class GradingJobORMService {

    public static class GradingJobAggregate {
        public Submission submission;
        public Score score;
        public AnalysisResult analysisResult;
        public List<RuleViolation> violations = new ArrayList<>();

        public GradingJobAggregate(Submission sub, Score score, AnalysisResult ar, List<RuleViolation> vios) {
            this.submission = sub;
            this.score = score;
            this.analysisResult = ar;
            this.violations = vios != null ? vios : new ArrayList<>();
        }

        @Override
        public String toString() {
            return String.format("GradingJobAggregate[Submission: %s (%s), Score: %.1f, AnalysisId: %d, Violations: %d]",
                    submission.getSubmissionId(), submission.getStatus(),
                    score != null ? score.getTotalScore() : -1.0f,
                    analysisResult != null ? analysisResult.getAnalysisId() : -1,
                    violations.size());
        }
    }

    /**
     * Atomically executes and commits a complete Grading Job unit of work.
     * Synchronizes Submission status, persists AnalysisResult, Score, and RuleViolations in a single ACID transaction.
     */
    public boolean processGradingJob(Submission submission, Score score, AnalysisResult analysis, List<RuleViolation> violations) throws SQLException {
        String updateSubSql = "UPDATE Submission SET status = ? WHERE submission_id = ?";
        String insertScoreSql = "IF EXISTS (SELECT 1 FROM Score WHERE submission_id = ?) " +
                                "UPDATE Score SET total_score = ?, deduction = ?, grading_notes = ?, updated_at = ? WHERE submission_id = ? " +
                                "ELSE INSERT INTO Score (score_id, submission_id, total_score, deduction, grading_notes, updated_at) VALUES (?,?,?,?,?,?)";
        String insertAnalSql = "IF EXISTS (SELECT 1 FROM AnalysisResult WHERE submission_id = ?) " +
                               "UPDATE AnalysisResult SET ast_json = ?, ai_feedback = ?, execution_time_ms = ? WHERE submission_id = ? " +
                               "ELSE INSERT INTO AnalysisResult (analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms) VALUES (?,?,?,?,?)";
        String insertVioSql = "INSERT INTO RuleViolation (violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix) VALUES (?,?,?,?,?,?)";

        try (Connection conn = DBConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); // BEGIN TRANSACTION

            try {
                // 1. Update Submission status
                submission.setStatus("Graded");
                try (PreparedStatement ps = conn.prepareStatement(updateSubSql)) {
                    ps.setString(1, submission.getStatus());
                    ps.setString(2, submission.getSubmissionId());
                    ps.executeUpdate();
                }

                // 2. Persist / Synchronize Score
                try (PreparedStatement ps = conn.prepareStatement(insertScoreSql)) {
                    ps.setString(1, score.getSubmissionId());
                    ps.setFloat(2, score.getTotalScore());
                    ps.setFloat(3, score.getDeduction());
                    ps.setString(4, score.getGradingNotes());
                    ps.setString(5, score.getUpdatedAt());
                    ps.setString(6, score.getSubmissionId());
                    ps.setInt(7, score.getScoreId());
                    ps.setString(8, score.getSubmissionId());
                    ps.setFloat(9, score.getTotalScore());
                    ps.setFloat(10, score.getDeduction());
                    ps.setString(11, score.getGradingNotes());
                    ps.setString(12, score.getUpdatedAt());
                    ps.executeUpdate();
                }

                // 3. Persist / Synchronize AnalysisResult
                try (PreparedStatement ps = conn.prepareStatement(insertAnalSql)) {
                    ps.setString(1, analysis.getSubmissionId());
                    ps.setString(2, analysis.getAstJson());
                    ps.setString(3, analysis.getAiFeedback());
                    ps.setInt(4, analysis.getExecutionTimeMs());
                    ps.setString(5, analysis.getSubmissionId());
                    ps.setInt(6, analysis.getAnalysisId());
                    ps.setString(7, analysis.getSubmissionId());
                    ps.setString(8, analysis.getAstJson());
                    ps.setString(9, analysis.getAiFeedback());
                    ps.setInt(10, analysis.getExecutionTimeMs());
                    ps.executeUpdate();
                }

                // 4. Persist RuleViolations
                if (violations != null && !violations.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(insertVioSql)) {
                        for (RuleViolation v : violations) {
                            ps.setInt(1, v.getViolationId());
                            ps.setInt(2, v.getAnalysisId());
                            ps.setInt(3, v.getRuleId());
                            ps.setString(4, v.getFileName());
                            ps.setInt(5, v.getLineNumber());
                            ps.setString(6, v.getSuggestedFix());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit(); // COMMIT TRANSACTION
                conn.setAutoCommit(originalAutoCommit);
                return true;
            } catch (Exception e) {
                conn.rollback(); // ROLLBACK ON ERROR
                conn.setAutoCommit(originalAutoCommit);
                throw new SQLException("GradingJob transaction rolled back due to error: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves the complete synchronized aggregate entity for a given submissionId.
     */
    public GradingJobAggregate getGradingJobAggregate(String submissionId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Submission sub = null;
            Score score = null;
            AnalysisResult anal = null;
            List<RuleViolation> violations = new ArrayList<>();

            // 1. Fetch Submission
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM Submission WHERE submission_id = ?")) {
                ps.setString(1, submissionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sub = new Submission(
                                rs.getString("submission_id"),
                                rs.getString("user_id"),
                                rs.getString("file_url"),
                                rs.getString("file_hash"),
                                rs.getString("status"),
                                rs.getString("submitted_at")
                        );
                    }
                }
            }
            if (sub == null) return null;

            // 2. Fetch Score
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM Score WHERE submission_id = ?")) {
                ps.setString(1, submissionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        score = new Score(
                                rs.getInt("score_id"),
                                rs.getString("submission_id"),
                                rs.getFloat("total_score"),
                                rs.getFloat("deduction"),
                                rs.getString("grading_notes"),
                                rs.getString("updated_at")
                        );
                    }
                }
            }

            // 3. Fetch AnalysisResult
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM AnalysisResult WHERE submission_id = ?")) {
                ps.setString(1, submissionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        anal = new AnalysisResult(
                                rs.getInt("analysis_id"),
                                rs.getString("submission_id"),
                                rs.getString("ast_json"),
                                rs.getString("ai_feedback"),
                                rs.getInt("execution_time_ms")
                        );
                    }
                }
            }

            // 4. Fetch RuleViolations if AnalysisResult exists
            if (anal != null) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM RuleViolation WHERE analysis_id = ?")) {
                    ps.setInt(1, anal.getAnalysisId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            violations.add(new RuleViolation(
                                    rs.getInt("violation_id"),
                                    rs.getInt("analysis_id"),
                                    rs.getInt("rule_id"),
                                    rs.getString("file_name"),
                                    rs.getInt("line_number"),
                                    rs.getString("suggested_fix")
                            ));
                        }
                    }
                }
            }

            return new GradingJobAggregate(sub, score, anal, violations);
        }
    }
}
