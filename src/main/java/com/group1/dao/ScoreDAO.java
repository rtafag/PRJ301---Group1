package com.group1.dao;
import com.group1.model.Score;
import java.sql.*;
public class ScoreDAO {
    private Connection conn;
    public ScoreDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Score' and xtype='U') " +
                      "CREATE TABLE Score (score_id INT PRIMARY KEY, submission_id VARCHAR(50), total_score FLOAT, deduction FLOAT, grading_notes NVARCHAR(MAX), updated_at DATETIME)");
        } catch(Exception e){}
    }
    public void insert(Score sc) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Score VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, sc.scoreId); ps.setString(2, sc.submissionId); ps.setFloat(3, sc.totalScore);
            ps.setFloat(4, sc.deduction); ps.setString(5, sc.gradingNotes); ps.setString(6, sc.updatedAt);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
