package com.group1.demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * DEMO SAMPLE 04 - AITA SYSTEM
 * Topic: Exemplary Student Submission (Clean Code & Best Practices)
 * Expected Result:
 *  - 0 Violations detected
 *  - Deduction: 0.0 pts
 *  - Final Score: 10.0 / 10.0
 *  - 0% False Positives
 */
public class Sample04_CleanSubmission_Good {

    private final String studentId;
    private final String fullName;

    /**
     * Initializes student record.
     * @param studentId unique student ID
     * @param fullName full name of student
     */
    public Sample04_CleanSubmission_Good(String studentId, String fullName) {
        this.studentId = studentId;
        this.fullName = fullName;
    }

    /**
     * Authenticates user using content comparison.
     * @param inputRole user role
     * @return true if authorized
     */
    public boolean checkAuthorization(String inputRole) {
        return "LECTURER".equalsIgnoreCase(inputRole) || "STUDENT".equalsIgnoreCase(inputRole);
    }

    /**
     * Safe database lookup using try-with-resources.
     * @param conn database connection
     * @param query SQL query
     * @throws SQLException on database error
     */
    public void queryStudentCount(Connection conn, String query) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.println("Row count processed safely.");
            }
        }
    }

    /**
     * Safe loop execution with guaranteed termination.
     * @param maxIterations upper bound
     */
    public void executeBatchProcessing(int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            System.out.printf("Batch item %d processed.%n", i);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sample04_CleanSubmission_Good that = (Sample04_CleanSubmission_Good) o;
        return Objects.equals(studentId, that.studentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId);
    }

    @Override
    public String toString() {
        return "Student[ID=" + studentId + ", Name=" + fullName + "]";
    }
}
