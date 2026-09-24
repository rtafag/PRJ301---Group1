package com.group1.dao;

import com.group1.model.UserAccount;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserAccountDAOImpl implements UserAccountDAO {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;";
    private static final String USER = "sa"; 
    private static final String PASS = "password123";

    public UserAccountDAOImpl() {
        createTableIfNotExists();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    private void createTableIfNotExists() {
        String sql = "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='user_accounts' and xtype='U') " +
                     "CREATE TABLE user_accounts (" +
                     "userId VARCHAR(50) PRIMARY KEY, " +
                     "username VARCHAR(100), " +
                     "email VARCHAR(100), " +
                     "role VARCHAR(50), " +
                     "status VARCHAR(50), " +
                     "password VARCHAR(100))";
                     
        String addUsernameSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='user_accounts' and xtype='U') " +
                                "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('user_accounts') AND name = 'username') " +
                                "ALTER TABLE user_accounts ADD username VARCHAR(100)";
        String addEmailSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='user_accounts' and xtype='U') " +
                             "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('user_accounts') AND name = 'email') " +
                             "ALTER TABLE user_accounts ADD email VARCHAR(100)";
        String addRoleSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='user_accounts' and xtype='U') " +
                            "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('user_accounts') AND name = 'role') " +
                            "ALTER TABLE user_accounts ADD role VARCHAR(50)";
        String addStatusSql = "IF EXISTS (SELECT * FROM sysobjects WHERE name='user_accounts' and xtype='U') " +
                              "AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('user_accounts') AND name = 'status') " +
                              "ALTER TABLE user_accounts ADD status VARCHAR(50)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(addUsernameSql);
            stmt.execute(addEmailSql);
            stmt.execute(addRoleSql);
            stmt.execute(addStatusSql);
        } catch (SQLException e) {
            System.err.println("Không thể tạo bảng user_accounts: " + e.getMessage());
        }
    }

    @Override
    public void insert(UserAccount account) {
        String sql = "INSERT INTO user_accounts (userId, username, email, role, status, password) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, account.getUserId());
            pstmt.setString(2, account.getUsername());
            pstmt.setString(3, account.getEmail());
            pstmt.setString(4, account.getRole());
            pstmt.setString(5, account.getStatus());
            pstmt.setString(6, account.getPassword());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public UserAccount getById(String userId) {
        String sql = "SELECT * FROM user_accounts WHERE userId = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new UserAccount(
                        rs.getString("userId"), 
                        rs.getString("username"), 
                        rs.getString("email"), 
                        rs.getString("role"), 
                        rs.getString("status"), 
                        rs.getString("password")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<UserAccount> getAll() {
        List<UserAccount> list = new ArrayList<>();
        String sql = "SELECT * FROM user_accounts";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(new UserAccount(
                    rs.getString("userId"), 
                    rs.getString("username"), 
                    rs.getString("email"), 
                    rs.getString("role"), 
                    rs.getString("status"), 
                    rs.getString("password")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public void truncateTable() {
        String sql = "TRUNCATE TABLE user_accounts";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
