package com.group1.dao;
import com.group1.model.Users;
import java.sql.*;
public class UsersDAO {
    private Connection conn;
    public UsersDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Users' and xtype='U') " +
                      "CREATE TABLE Users (user_id VARCHAR(50) PRIMARY KEY, username VARCHAR(100), password_hash VARCHAR(100), full_name NVARCHAR(100), email VARCHAR(100), role VARCHAR(50), created_at DATETIME)");
        } catch(Exception e){}
    }
    public void insert(Users u) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Users VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, u.userId); ps.setString(2, u.username); ps.setString(3, u.passwordHash);
            ps.setString(4, u.fullName); ps.setString(5, u.email); ps.setString(6, u.role); ps.setString(7, u.createdAt);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
