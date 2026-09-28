package com.group1;

import com.group1.dao.*;
import com.group1.model.*;
import com.group1.util.CSVHelper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String url = "jdbc:sqlserver://localhost:1433;databaseName=AITA_DB;encrypt=false;trustServerCertificate=true;";
        try (Connection conn = DriverManager.getConnection(url, "sa", "password123")) {
            System.out.println("Connected to SQL Server!");

            UsersDAO usersDAO = new UsersDAO(conn);
            RuleDefinitionDAO ruleDefDAO = new RuleDefinitionDAO(conn);
            SubmissionDAO subDAO = new SubmissionDAO(conn);
            ScoreDAO scoreDAO = new ScoreDAO(conn);
            AnalysisResultDAO analDAO = new AnalysisResultDAO(conn);
            RuleViolationDAO ruleVioDAO = new RuleViolationDAO(conn);

            System.out.println("Inserting Users...");
            List<Users> users = CSVHelper.readUsers("data/users.csv");
            for(Users u : users) {
                usersDAO.insert(u);
            }

            System.out.println("Inserting RuleDefinitions...");
            List<RuleDefinition> rules = CSVHelper.readRuleDefinitions("data/rule_definition.csv");
            for(RuleDefinition r : rules) {
                ruleDefDAO.insert(r);
            }

            System.out.println("Inserting Submissions...");
            List<Submission> subs = CSVHelper.readSubmissions("data/submission.csv");
            for(Submission s : subs) {
                subDAO.insert(s);
            }

            System.out.println("Inserting Scores...");
            List<Score> scores = CSVHelper.readScores("data/score.csv");
            for(Score s : scores) {
                scoreDAO.insert(s);
            }

            System.out.println("Inserting AnalysisResults...");
            List<AnalysisResult> anals = CSVHelper.readAnalysisResults("data/analysis_result.csv");
            for(AnalysisResult a : anals) {
                analDAO.insert(a);
            }

            System.out.println("Inserting RuleViolations...");
            List<RuleViolation> vios = CSVHelper.readRuleViolations("data/rule_violation.csv");
            for(RuleViolation v : vios) {
                ruleVioDAO.insert(v);
            }

            System.out.println("Database fully populated with ERD tables!");

            // --- IN KẾT QUẢ TỪ SQL SERVER RA MÀN HÌNH ---
            System.out.println("\n=== SQL SERVER SCORES ===");
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery("SELECT TOP 10 submission_id, total_score FROM Score")) {
                while (rs.next()) {
                    System.out.println("Submission: " + rs.getString("submission_id") + 
                                       "  --->  Score: " + rs.getFloat("total_score"));
                }
                System.out.println("... (Showing top 10 only)");
            }
            System.out.println("=====================================\n");

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
