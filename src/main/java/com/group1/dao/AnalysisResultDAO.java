package com.group1.dao;
import com.group1.model.AnalysisResult;
import java.sql.*;
public class AnalysisResultDAO {
    private Connection conn;
    public AnalysisResultDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='AnalysisResult' and xtype='U') " +
                      "CREATE TABLE AnalysisResult (analysis_id INT PRIMARY KEY, submission_id VARCHAR(50), ast_json NVARCHAR(MAX), ai_feedback NVARCHAR(MAX), execution_time_ms INT)");
        } catch(Exception e){}
    }
    public void insert(AnalysisResult ar) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO AnalysisResult VALUES (?,?,?,?,?)")) {
            ps.setInt(1, ar.analysisId); ps.setString(2, ar.submissionId); ps.setString(3, ar.astJson);
            ps.setString(4, ar.aiFeedback); ps.setInt(5, ar.executionTimeMs);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
