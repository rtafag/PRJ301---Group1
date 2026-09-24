package com.group1.dao;

import com.group1.model.Submission;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SubmissionDAOImpl implements SubmissionDAO {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;";
    private static final String USER = "sa"; 
    private static final String PASS = "password123";

    public SubmissionDAOImpl() {
        createTableIfNotExists();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    private void createTableIfNotExists() {
        String sql = "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='submissions' and xtype='U') " +
                     "CREATE TABLE submissions (" +
                     "submissionId VARCHAR(100) PRIMARY KEY, " +
                     "studentId VARCHAR(50), " +
                     "teacherId VARCHAR(50), " +
                     "submitTime VARCHAR(100), " +
                     "score FLOAT, " +
                     "scorePublicTime VARCHAR(100))";

        String addStudentIdSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='submissions' and xtype='U') " +
                                 "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('submissions') AND name = 'studentId') " +
                                 "ALTER TABLE submissions ADD studentId VARCHAR(50)";
        
        String addTeacherIdSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='submissions' and xtype='U') " +
                                 "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('submissions') AND name = 'teacherId') " +
                                 "ALTER TABLE submissions ADD teacherId VARCHAR(50)";
                                 
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(addStudentIdSql);
            stmt.execute(addTeacherIdSql);
        } catch (SQLException e) {
            System.err.println("Không thể tạo bảng submissions: " + e.getMessage());
        }
    }

    @Override
    public void insert(Submission submission) {
        String sql = "INSERT INTO submissions (submissionId, studentId, teacherId, submitTime, score, scorePublicTime) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, submission.getSubmissionId());
            pstmt.setString(2, submission.getStudentId());
            pstmt.setString(3, submission.getTeacherId());
            pstmt.setString(4, submission.getSubmitTime());
            pstmt.setDouble(5, submission.getScore());
            pstmt.setString(6, submission.getScorePublicTime());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Submission getById(String submissionId) {
        String sql = "SELECT * FROM submissions WHERE submissionId = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, submissionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Submission(
                        rs.getString("submissionId"),
                        rs.getString("studentId"),
                        rs.getString("teacherId"),
                        rs.getString("submitTime"),
                        rs.getDouble("score"),
                        rs.getString("scorePublicTime")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Submission> getAll() {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT * FROM submissions";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                list.add(new Submission(
                    rs.getString("submissionId"),
                    rs.getString("studentId"),
                    rs.getString("teacherId"),
                    rs.getString("submitTime"),
                    rs.getDouble("score"),
                    rs.getString("scorePublicTime")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void updateScore(String submissionId, double newScore, String scorePublicTime) {
        String sql = "UPDATE submissions SET score = ?, scorePublicTime = ? WHERE submissionId = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, newScore);
            pstmt.setString(2, scorePublicTime);
            pstmt.setString(3, submissionId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void truncateTable() {
        String sql = "TRUNCATE TABLE submissions";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
