package com.group1.dao;

import com.group1.model.AnalysisResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AnalysisResultDAO {
    private final Connection conn;

    public AnalysisResultDAO(Connection c) {
        this.conn = c;
        createTable();
    }

    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='AnalysisResult' and xtype='U') " +
                    "CREATE TABLE AnalysisResult (analysis_id INT PRIMARY KEY, submission_id VARCHAR(50), ast_json NVARCHAR(MAX), ai_feedback NVARCHAR(MAX), execution_time_ms INT)");
        } catch (Exception ignored) {
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
