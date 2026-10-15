package com.group1.dao;

import com.group1.model.RuleDefinition;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RuleDefinitionDAO {
    private final Connection conn;

    public RuleDefinitionDAO(Connection c) {
        this.conn = c;
        createTable();
    }

    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RuleDefinition' and xtype='U') " +
                    "CREATE TABLE RuleDefinition (rule_id INT PRIMARY KEY, rule_code VARCHAR(50), rule_name NVARCHAR(200), severity VARCHAR(50), penalty_points FLOAT, description NVARCHAR(MAX))");
        } catch (Exception ignored) {
        }
    }

    public void insert(RuleDefinition r) {
        String sql = "INSERT INTO RuleDefinition (rule_id, rule_code, rule_name, severity, penalty_points, description) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.ruleId);
            ps.setString(2, r.ruleCode);
            ps.setString(3, r.ruleName);
            ps.setString(4, r.severity);
            ps.setFloat(5, r.penaltyPoints);
            ps.setString(6, r.description);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<RuleDefinition> findAll() throws SQLException {
        List<RuleDefinition> list = new ArrayList<>();
        String sql = "SELECT rule_id, rule_code, rule_name, severity, penalty_points, description FROM RuleDefinition ORDER BY rule_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public RuleDefinition findById(int ruleId) throws SQLException {
        String sql = "SELECT rule_id, rule_code, rule_name, severity, penalty_points, description FROM RuleDefinition WHERE rule_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public RuleDefinition findByCode(String code) throws SQLException {
        String sql = "SELECT rule_id, rule_code, rule_name, severity, penalty_points, description FROM RuleDefinition WHERE rule_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM RuleDefinition";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private RuleDefinition mapRow(ResultSet rs) throws SQLException {
        return new RuleDefinition(
                rs.getInt("rule_id"),
                rs.getString("rule_code"),
                rs.getString("rule_name"),
                rs.getString("severity"),
                rs.getFloat("penalty_points"),
                rs.getString("description")
        );
    }
}
