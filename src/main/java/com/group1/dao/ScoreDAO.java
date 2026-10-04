package com.group1.dao;

import com.group1.model.Score;
import java.sql.*;

public class ScoreDAO {
    private Connection conn;

    public ScoreDAO(Connection c) {
        this.conn = c;
        createTable();
    }

    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Score' and xtype='U') " +
                    "CREATE TABLE Score (score_id INT PRIMARY KEY, submission_id VARCHAR(50), total_score FLOAT, deduction FLOAT, grading_notes NVARCHAR(MAX), updated_at DATETIME)");
        } catch (Exception e) {
        }
    }

    public void insert(Score sc) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Score VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, sc.scoreId);
            ps.setString(2, sc.submissionId);
            ps.setFloat(3, sc.totalScore);
            ps.setFloat(4, sc.deduction);
            ps.setString(5, sc.gradingNotes);
            ps.setString(6, sc.updatedAt);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public java.util.List<Score> findAll() throws SQLException {
        java.util.List<Score> scores = new java.util.ArrayList<>();
        String sql = "SELECT score_id, submission_id, total_score, deduction, grading_notes, updated_at "
                + "FROM Score ORDER BY score_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                scores.add(new Score(
                        rs.getInt("score_id"),
                        rs.getString("submission_id"),
                        rs.getFloat("total_score"),
                        rs.getFloat("deduction"),
                        rs.getString("grading_notes"),
                        rs.getString("updated_at")));
            }
        }
        return scores;
    }

    public java.util.List<Score> findByUserId(String userId) throws SQLException {
        java.util.List<Score> scores = new java.util.ArrayList<>();
        String sql = "SELECT score.score_id, score.submission_id, score.total_score, score.deduction, "
                + "score.grading_notes, score.updated_at "
                + "FROM Score score "
                + "INNER JOIN Submission submission ON submission.submission_id = score.submission_id "
                + "WHERE submission.user_id = ? ORDER BY score.score_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scores.add(new Score(
                            rs.getInt("score_id"),
                            rs.getString("submission_id"),
                            rs.getFloat("total_score"),
                            rs.getFloat("deduction"),
                            rs.getString("grading_notes"),
                            rs.getString("updated_at")));
                }
            }
        }
        return scores;
    }
}
