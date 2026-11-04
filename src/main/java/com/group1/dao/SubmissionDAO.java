package com.group1.dao;

import com.group1.model.Submission;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SubmissionDAO {
    private final Connection conn;

    public SubmissionDAO(Connection c) throws SQLException {
        this.conn = c;
        createTable();
    }

    private void createTable() throws SQLException {
        String sql = "IF OBJECT_ID('dbo.Submission', 'U') IS NULL "
                + "CREATE TABLE dbo.Submission (submission_id VARCHAR(50) PRIMARY KEY, "
                + "user_id VARCHAR(50) NOT NULL, file_url VARCHAR(MAX) NOT NULL, "
                + "file_hash VARCHAR(100) NOT NULL, status VARCHAR(50) NOT NULL, "
                + "submitted_at DATETIME NOT NULL)";
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    public void insert(Submission submission) throws SQLException {
        String sql = "INSERT INTO Submission "
                + "(submission_id, user_id, file_url, file_hash, status, submitted_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, submission.getSubmissionId());
            ps.setString(2, submission.getUserId());
            ps.setString(3, submission.getFileUrl());
            ps.setString(4, submission.getFileHash());
            ps.setString(5, submission.getStatus());
            ps.setTimestamp(6, Timestamp.valueOf(submission.getSubmittedAt()));
            ps.executeUpdate();
        }
    }

    public List<Submission> findByUserId(String userId) throws SQLException {
        List<Submission> submissions = new ArrayList<>();
        String sql = "SELECT submission_id, user_id, file_url, file_hash, status, submitted_at "
                + "FROM Submission WHERE user_id = ? ORDER BY submitted_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    submissions.add(new Submission(
                            rs.getString("submission_id"),
                            rs.getString("user_id"),
                            rs.getString("file_url"),
                            rs.getString("file_hash"),
                            rs.getString("status"),
                            rs.getTimestamp("submitted_at").toLocalDateTime().toString().replace('T', ' ')));
                }
            }
        }
        return submissions;
    }
}
