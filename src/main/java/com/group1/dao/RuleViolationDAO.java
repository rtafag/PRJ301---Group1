package com.group1.dao;

import com.group1.model.RuleViolation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RuleViolationDAO {
    private final Connection conn;

    public RuleViolationDAO(Connection c) {
        this.conn = c;
        createTable();
    }

    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RuleViolation' and xtype='U') " +
                    "CREATE TABLE RuleViolation (violation_id INT PRIMARY KEY, analysis_id INT, rule_id INT, file_name VARCHAR(200), line_number INT, suggested_fix NVARCHAR(MAX))");
        } catch (Exception ignored) {
        }
    }

    public void insert(RuleViolation rv) {
        String sql = "INSERT INTO RuleViolation (violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rv.violationId);
            ps.setInt(2, rv.analysisId);
            ps.setInt(3, rv.ruleId);
            ps.setString(4, rv.fileName);
            ps.setInt(5, rv.lineNumber);
            ps.setString(6, rv.suggestedFix);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<RuleViolation> findAll() throws SQLException {
        List<RuleViolation> list = new ArrayList<>();
        String sql = "SELECT violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix FROM RuleViolation ORDER BY violation_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public RuleViolation findById(int violationId) throws SQLException {
        String sql = "SELECT violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix FROM RuleViolation WHERE violation_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, violationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<RuleViolation> findByAnalysisId(int analysisId) throws SQLException {
        List<RuleViolation> list = new ArrayList<>();
        String sql = "SELECT violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix FROM RuleViolation WHERE analysis_id = ? ORDER BY violation_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, analysisId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM RuleViolation";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private RuleViolation mapRow(ResultSet rs) throws SQLException {
        return new RuleViolation(
                rs.getInt("violation_id"),
                rs.getInt("analysis_id"),
                rs.getInt("rule_id"),
                rs.getString("file_name"),
                rs.getInt("line_number"),
                rs.getString("suggested_fix")
        );
    }
}
