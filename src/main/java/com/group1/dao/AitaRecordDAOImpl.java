package com.group1.dao;

import com.group1.model.AitaRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AitaRecordDAOImpl implements AitaRecordDAO {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;";
    private static final String USER = "sa"; 
    private static final String PASS = "password123";

    public AitaRecordDAOImpl() {
        createTableIfNotExists();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    private void createTableIfNotExists() {
        String sql = "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='aita_records' and xtype='U') " +
                     "CREATE TABLE aita_records (" +
                     "dbId INT PRIMARY KEY IDENTITY(1,1), " +
                     "userId VARCHAR(50), " +
                     "name NVARCHAR(100) NOT NULL, " +
                     "email VARCHAR(100), " +
                     "age INT, " +
                     "role NVARCHAR(50), " +
                     "status VARCHAR(50))"; // active / offline
        
        String addStatusSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='aita_records' and xtype='U') " +
                          "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('aita_records') AND name = 'status') " +
                          "ALTER TABLE aita_records ADD status VARCHAR(50)";

        String dropAnalystIdSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='aita_records' and xtype='U') " +
                                  "AND EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('aita_records') AND name = 'analystId') " +
                                  "ALTER TABLE aita_records DROP COLUMN analystId";

        String dropSubmissionIdSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='aita_records' and xtype='U') " +
                                     "AND EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('aita_records') AND name = 'submissionId') " +
                                     "ALTER TABLE aita_records DROP COLUMN submissionId";
                          
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(addStatusSql);
            stmt.execute(dropAnalystIdSql);
            stmt.execute(dropSubmissionIdSql);
        } catch (SQLException e) {
            System.err.println("Không thể khởi tạo hoặc cập nhật bảng aita_records: " + e.getMessage());
        }
    }

    @Override
    public void insert(AitaRecord record) {
        String sql = "INSERT INTO aita_records (userId, name, email, age, role, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, record.getUserId());
            pstmt.setString(2, record.getName());
            pstmt.setString(3, record.getEmail());
            pstmt.setInt(4, record.getAge());
            pstmt.setString(5, record.getRole());
            pstmt.setString(6, record.getStatus());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public AitaRecord getById(int dbId) {
        String sql = "SELECT * FROM aita_records WHERE dbId = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, dbId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new AitaRecord(
                        rs.getInt("dbId"),
                        rs.getString("userId"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getInt("age"),
                        rs.getString("role"),
                        rs.getString("status")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<AitaRecord> getAll() {
        List<AitaRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM aita_records";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                list.add(new AitaRecord(
                    rs.getInt("dbId"),
                    rs.getString("userId"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getInt("age"),
                    rs.getString("role"),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void update(AitaRecord record) {
        String sql = "UPDATE aita_records SET userId=?, name=?, email=?, age=?, role=?, status=? WHERE dbId=?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, record.getUserId());
            pstmt.setString(2, record.getName());
            pstmt.setString(3, record.getEmail());
            pstmt.setInt(4, record.getAge());
            pstmt.setString(5, record.getRole());
            pstmt.setString(6, record.getStatus());
            pstmt.setInt(7, record.getDbId());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(int dbId) {
        String sql = "DELETE FROM aita_records WHERE dbId=?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, dbId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void truncateTable() {
        String sql = "TRUNCATE TABLE aita_records";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
