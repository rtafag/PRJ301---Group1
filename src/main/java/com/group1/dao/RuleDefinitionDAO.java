package com.group1.dao;
import com.group1.model.RuleDefinition;
import java.sql.*;
public class RuleDefinitionDAO {
    private Connection conn;
    public RuleDefinitionDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RuleDefinition' and xtype='U') " +
                      "CREATE TABLE RuleDefinition (rule_id INT PRIMARY KEY, rule_code VARCHAR(50), rule_name NVARCHAR(200), severity VARCHAR(50), penalty_points FLOAT, description NVARCHAR(MAX))");
        } catch(Exception e){}
    }
    public void insert(RuleDefinition r) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO RuleDefinition VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, r.ruleId); ps.setString(2, r.ruleCode); ps.setString(3, r.ruleName);
            ps.setString(4, r.severity); ps.setFloat(5, r.penaltyPoints); ps.setString(6, r.description);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
