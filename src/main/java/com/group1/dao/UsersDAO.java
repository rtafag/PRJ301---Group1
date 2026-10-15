package com.group1.dao;

import com.group1.model.Users;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class UsersDAO {
    private final Connection conn;

    public UsersDAO(Connection c) {
        this.conn = c;
        createTable();
    }

    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Users' and xtype='U') " +
                    "CREATE TABLE Users (user_id VARCHAR(50) PRIMARY KEY, username VARCHAR(100), password_hash VARCHAR(100), full_name NVARCHAR(100), email VARCHAR(100), role VARCHAR(50), created_at DATETIME)");
        } catch (Exception ignored) {
        }
    }

    public void insert(Users u) {
        String sql = "INSERT INTO Users (user_id, username, password_hash, full_name, email, role, created_at) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.userId);
            ps.setString(2, u.username);
            ps.setString(3, u.passwordHash);
            ps.setString(4, u.fullName);
            ps.setString(5, u.email);
            ps.setString(6, u.role);
            ps.setString(7, u.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Users> findAll() throws SQLException {
        List<Users> list = new ArrayList<>();
        String sql = "SELECT user_id, username, password_hash, full_name, email, role, created_at FROM Users ORDER BY user_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Users findById(String userId) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, full_name, email, role, created_at FROM Users WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Users findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, full_name, email, role, created_at FROM Users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Users login(String username, String passwordHash) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, full_name, email, role, created_at FROM Users WHERE username = ? AND password_hash = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM Users";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private Users mapRow(ResultSet rs) throws SQLException {
        return new Users(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("created_at")
        );
    }
}
