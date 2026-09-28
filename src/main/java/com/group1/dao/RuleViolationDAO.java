package com.group1.dao;
import com.group1.model.RuleViolation;
import java.sql.*;
public class RuleViolationDAO {
    private Connection conn;
    public RuleViolationDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RuleViolation' and xtype='U') " +
                      "CREATE TABLE RuleViolation (violation_id INT PRIMARY KEY, analysis_id INT, rule_id INT, file_name VARCHAR(200), line_number INT, suggested_fix NVARCHAR(MAX))");
        } catch(Exception e){}
    }
    public void insert(RuleViolation rv) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO RuleViolation VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, rv.violationId); ps.setInt(2, rv.analysisId); ps.setInt(3, rv.ruleId);
            ps.setString(4, rv.fileName); ps.setInt(5, rv.lineNumber); ps.setString(6, rv.suggestedFix);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
