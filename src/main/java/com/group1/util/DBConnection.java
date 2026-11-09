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
        String username = getEnvOrDefault("DB_USER", "sa");
        String password = getEnvOrDefault("DB_PASSWORD", "password123");
        return DriverManager.getConnection(url, username, password);
    }

    private static String getEnvOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
