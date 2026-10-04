package com.group1.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private static final String DEFAULT_URL = "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv().getOrDefault("DB_URL", DEFAULT_URL);
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || password == null) {
            throw new SQLException(
                    "Set DB_USER and DB_PASSWORD environment variables before starting the application.");
        }
        return DriverManager.getConnection(url, username, password);
    }
}