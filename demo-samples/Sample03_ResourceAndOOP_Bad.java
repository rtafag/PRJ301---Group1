package com.group1.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * DEMO SAMPLE 03 - AITA SYSTEM
 * Topic: PRJ301 Resource Leaks & Java OOP Contract Violations
 * Expected Violations:
 *  - [CS] Line 16: Class name starts with lowercase letter
 *  - [RES_LEAK] Line 19: JDBC Connection not managed via try-with-resources
 *  - [NPE] Line 25: Returns null without nullable check
 *  - [MISSING_OVERRIDE] Line 30: Standard method toString() missing @Override
 *  - [EQUALS_HASHCODE] Line 16: Class overrides equals() but fails to override hashCode()
 */
public class sample03_ResourceAndOOP_Bad {

    public void queryUserData() throws SQLException {
        // Bug 1: Resource leak - Connection unclosed
        Connection conn = DriverManager.getConnection("jdbc:sqlserver://localhost:1433;databaseName=AITA_DB");
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM Users");
        // No ps.close() or try-with-resources!
    }

    public Object findStudent() {
        // Bug 2: Return null
        return null;
    }

    // Bug 3: Missing @Override
    public String toString() {
        return "sample03";
    }

    // Bug 4: equals without hashCode
    public boolean equals(Object o) {
        return true;
    }
}
