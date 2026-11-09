package com.group1.dao;

import com.group1.model.AnalysisResult;
import com.group1.model.AnalysisDashboardItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalysisResultDAO {
    private final Connection conn;

    public AnalysisResultDAO(Connection c) throws SQLException {
        this.conn = c;
        createTable();
    }

    private void createTable() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='AnalysisResult' and xtype='U') " +
                    "CREATE TABLE AnalysisResult (analysis_id INT PRIMARY KEY, submission_id VARCHAR(50), ast_json NVARCHAR(MAX), ai_feedback NVARCHAR(MAX), execution_time_ms INT)");
        }
    }

    public void insert(AnalysisResult ar) {
        String sql = "INSERT INTO AnalysisResult (analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ar.analysisId);
            ps.setString(2, ar.submissionId);
            ps.setString(3, ar.astJson);
            ps.setString(4, ar.aiFeedback);
            ps.setInt(5, ar.executionTimeMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<AnalysisResult> findAll() throws SQLException {
        List<AnalysisResult> list = new ArrayList<>();
        String sql = "SELECT analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms FROM AnalysisResult ORDER BY analysis_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public AnalysisResult findById(int analysisId) throws SQLException {
        String sql = "SELECT analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms FROM AnalysisResult WHERE analysis_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, analysisId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public AnalysisResult findBySubmissionId(String submissionId) throws SQLException {
        String sql = "SELECT analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms FROM AnalysisResult WHERE submission_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<AnalysisDashboardItem> findDashboardByUserId(String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("A user ID is required to load a student dashboard.");
        }
        return findDashboard(userId.trim());
    }

    public List<AnalysisDashboardItem> findAllForDashboard() throws SQLException {
        return findDashboard(null);
    }

    private List<AnalysisDashboardItem> findDashboard(String userId) throws SQLException {
        String sql = "SELECT ar.analysis_id, ar.submission_id, ar.ai_feedback, ar.execution_time_ms, "
                + "rv.violation_id, rv.file_name, rv.line_number, rv.suggested_fix, "
                + "rd.rule_code, rd.rule_name, rd.severity, rd.penalty_points, rd.description "
                + "FROM AnalysisResult ar "
                + "INNER JOIN Submission s ON s.submission_id = ar.submission_id "
                + "LEFT JOIN RuleViolation rv ON rv.analysis_id = ar.analysis_id "
                + "LEFT JOIN RuleDefinition rd ON rd.rule_id = rv.rule_id "
                + (userId == null ? "" : "WHERE s.user_id = ? ")
                + "ORDER BY s.submitted_at DESC, ar.analysis_id DESC, rv.violation_id";
        Map<Integer, AnalysisDashboardItem> itemsById = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setString(1, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int analysisId = rs.getInt("analysis_id");
                    AnalysisDashboardItem item = itemsById.get(analysisId);
                    if (item == null) {
                        item = new AnalysisDashboardItem(
                                analysisId,
                                rs.getString("submission_id"),
                                rs.getString("ai_feedback"),
                                rs.getInt("execution_time_ms"));
                        itemsById.put(analysisId, item);
                    }

                    if (rs.getObject("violation_id") != null) {
                        item.addFinding(new AnalysisDashboardItem.Finding(
                                rs.getString("rule_code"),
                                rs.getString("rule_name"),
                                rs.getString("severity"),
                                rs.getFloat("penalty_points"),
                                rs.getString("description"),
                                rs.getString("file_name"),
                                rs.getInt("line_number"),
                                rs.getString("suggested_fix")));
                    }
                }
            }
        }
        return new ArrayList<>(itemsById.values());
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM AnalysisResult";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private AnalysisResult mapRow(ResultSet rs) throws SQLException {
        return new AnalysisResult(
                rs.getInt("analysis_id"),
                rs.getString("submission_id"),
                rs.getString("ast_json"),
                rs.getString("ai_feedback"),
                rs.getInt("execution_time_ms")
        );
    }
}
