package com.group1.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private static final String DEFAULT_URL = "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException ignored) {
        }
        String url = System.getenv().getOrDefault("DB_URL", DEFAULT_URL);
        String username = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "sa";
        String password = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "password123";
        return DriverManager.getConnection(url, username, password);
    }
}