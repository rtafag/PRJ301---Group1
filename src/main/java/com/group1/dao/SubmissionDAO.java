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
import java.util.ArrayList;
import java.util.List;

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
                return mapRow(result);
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
            try {
                ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(sub.submittedAt, SUBMITTED_AT_FORMAT)));
            } catch (Exception e) {
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            }
            ps.executeUpdate();
        }
    }

    public List<Submission> findAll() throws SQLException {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT submission_id, user_id, file_url, file_hash, status, submitted_at FROM Submission ORDER BY submitted_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Submission findById(String submissionId) throws SQLException {
        String sql = "SELECT submission_id, user_id, file_url, file_hash, status, submitted_at FROM Submission WHERE submission_id = ?";
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

    public List<Submission> findByUserId(String userId) throws SQLException {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT submission_id, user_id, file_url, file_hash, status, submitted_at FROM Submission WHERE user_id = ? ORDER BY submitted_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public void updateStatus(String submissionId, String status) throws SQLException {
        String sql = "UPDATE Submission SET status = ? WHERE submission_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, submissionId);
            ps.executeUpdate();
        }
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Submission";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private Submission mapRow(ResultSet rs) throws SQLException {
        Timestamp submittedAt = rs.getTimestamp("submitted_at");
        return new Submission(
                rs.getString("submission_id"),
                rs.getString("user_id"),
                rs.getString("file_url"),
                rs.getString("file_hash"),
                rs.getString("status"),
                submittedAt == null ? null : submittedAt.toLocalDateTime().format(SUBMITTED_AT_FORMAT)
        );
    }
}
