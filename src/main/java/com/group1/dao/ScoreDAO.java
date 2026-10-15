package com.group1.dao;

import com.group1.model.Score;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ScoreDAO {
    private final Connection conn;

    public ScoreDAO(Connection c) {
        this.conn = c;
        createTable();
    }

    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Score' and xtype='U') " +
                    "CREATE TABLE Score (score_id INT PRIMARY KEY, submission_id VARCHAR(50), total_score FLOAT, deduction FLOAT, grading_notes NVARCHAR(MAX), updated_at DATETIME)");
        } catch (Exception ignored) {
        }
    }

    public void insert(Score sc) {
        String sql = "INSERT INTO Score (score_id, submission_id, total_score, deduction, grading_notes, updated_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sc.scoreId);
            ps.setString(2, sc.submissionId);
            ps.setFloat(3, sc.totalScore);
            ps.setFloat(4, sc.deduction);
            ps.setString(5, sc.gradingNotes);
            ps.setString(6, sc.updatedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Score> findAll() throws SQLException {
        List<Score> scores = new ArrayList<>();
        String sql = "SELECT score_id, submission_id, total_score, deduction, grading_notes, updated_at "
                + "FROM Score ORDER BY score_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                scores.add(mapRow(rs));
            }
        }
        return scores;
    }

    public Score findById(int scoreId) throws SQLException {
        String sql = "SELECT score_id, submission_id, total_score, deduction, grading_notes, updated_at "
                + "FROM Score WHERE score_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scoreId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Score findBySubmissionId(String submissionId) throws SQLException {
        String sql = "SELECT score_id, submission_id, total_score, deduction, grading_notes, updated_at "
                + "FROM Score WHERE submission_id = ?";
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

    public List<Score> findByUserId(String userId) throws SQLException {
        List<Score> scores = new ArrayList<>();
        String sql = "SELECT score.score_id, score.submission_id, score.total_score, score.deduction, "
                + "score.grading_notes, score.updated_at "
                + "FROM Score score "
                + "INNER JOIN Submission submission ON submission.submission_id = score.submission_id "
                + "WHERE submission.user_id = ? ORDER BY score.score_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scores.add(mapRow(rs));
                }
            }
        }
        return scores;
    }

    public void update(Score sc) throws SQLException {
        String sql = "UPDATE Score SET total_score = ?, deduction = ?, grading_notes = ?, updated_at = ? WHERE score_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setFloat(1, sc.totalScore);
            ps.setFloat(2, sc.deduction);
            ps.setString(3, sc.gradingNotes);
            ps.setString(4, sc.updatedAt);
            ps.setInt(5, sc.scoreId);
            ps.executeUpdate();
        }
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Score";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private Score mapRow(ResultSet rs) throws SQLException {
        return new Score(
                rs.getInt("score_id"),
                rs.getString("submission_id"),
                rs.getFloat("total_score"),
                rs.getFloat("deduction"),
                rs.getString("grading_notes"),
                rs.getString("updated_at")
        );
    }
}
