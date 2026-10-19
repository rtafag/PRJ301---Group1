package com.group1.dao;

import com.group1.model.Submission;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SubmissionDAO {
    private final Connection conn;
    private static final DateTimeFormatter SUBMITTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SubmissionDAO(Connection c) throws SQLException {
        this.conn = c;
        createTable();
    }

    private void createTable() throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Submission' and xtype='U') "
                    + "CREATE TABLE Submission (submission_id VARCHAR(50) PRIMARY KEY, user_id VARCHAR(50), "
                    + "file_url VARCHAR(MAX), file_hash VARCHAR(100), status VARCHAR(50), submitted_at DATETIME)");
        }
    }

    public Submission findByFileHash(String fileHash) throws SQLException {
        String sql = "SELECT TOP 1 submission_id, user_id, file_url, file_hash, status, submitted_at "
                + "FROM Submission WHERE file_hash = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, fileHash);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                Timestamp submittedAt = result.getTimestamp("submitted_at");
                return new Submission(
                        result.getString("submission_id"),
                        result.getString("user_id"),
                        result.getString("file_url"),
                        result.getString("file_hash"),
                        result.getString("status"),
                        submittedAt == null ? null : submittedAt.toLocalDateTime().format(SUBMITTED_AT_FORMAT));
            }
        }
    }

    public void insert(Submission sub) throws SQLException {
        String sql = "INSERT INTO Submission "
                + "(submission_id, user_id, file_url, file_hash, status, submitted_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sub.submissionId);
            ps.setString(2, sub.userId);
            ps.setString(3, sub.fileUrl);
            ps.setString(4, sub.fileHash);
            ps.setString(5, sub.status);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(sub.submittedAt, SUBMITTED_AT_FORMAT)));
            ps.executeUpdate();
        }
    }
}
