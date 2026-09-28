package com.group1.dao;
import com.group1.model.Submission;
import java.sql.*;
public class SubmissionDAO {
    private Connection conn;
    public SubmissionDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Submission' and xtype='U') " +
                      "CREATE TABLE Submission (submission_id VARCHAR(50) PRIMARY KEY, user_id VARCHAR(50), file_url VARCHAR(MAX), file_hash VARCHAR(100), status VARCHAR(50), submitted_at DATETIME)");
        } catch(Exception e){}
    }
    public void insert(Submission sub) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Submission VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, sub.submissionId); ps.setString(2, sub.userId); ps.setString(3, sub.fileUrl);
            ps.setString(4, sub.fileHash); ps.setString(5, sub.status); ps.setString(6, sub.submittedAt);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
