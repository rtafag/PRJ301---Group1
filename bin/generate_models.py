import os

models_dir = r"src\main\java\com\group1\model"
daos_dir = r"src\main\java\com\group1\dao"
util_dir = r"src\main\java\com\group1\util"
root_dir = r"src\main\java\com\group1"

os.makedirs(models_dir, exist_ok=True)
os.makedirs(daos_dir, exist_ok=True)

# 1. MODELS
models = {
    "Users.java": """package com.group1.model;
public class Users {
    public String userId; public String username; public String passwordHash; 
    public String fullName; public String email; public String role; public String createdAt;
    public Users(String userId, String username, String passwordHash, String fullName, String email, String role, String createdAt) {
        this.userId=userId; this.username=username; this.passwordHash=passwordHash; 
        this.fullName=fullName; this.email=email; this.role=role; this.createdAt=createdAt;
    }
}
""",
    "RuleDefinition.java": """package com.group1.model;
public class RuleDefinition {
    public int ruleId; public String ruleCode; public String ruleName;
    public String severity; public float penaltyPoints; public String description;
    public RuleDefinition(int ruleId, String ruleCode, String ruleName, String severity, float penaltyPoints, String description) {
        this.ruleId=ruleId; this.ruleCode=ruleCode; this.ruleName=ruleName;
        this.severity=severity; this.penaltyPoints=penaltyPoints; this.description=description;
    }
}
""",
    "Submission.java": """package com.group1.model;
public class Submission {
    public String submissionId; public String userId; public String fileUrl;
    public String fileHash; public String status; public String submittedAt;
    public Submission(String submissionId, String userId, String fileUrl, String fileHash, String status, String submittedAt) {
        this.submissionId=submissionId; this.userId=userId; this.fileUrl=fileUrl;
        this.fileHash=fileHash; this.status=status; this.submittedAt=submittedAt;
    }
}
""",
    "Score.java": """package com.group1.model;
public class Score {
    public int scoreId; public String submissionId; public float totalScore;
    public float deduction; public String gradingNotes; public String updatedAt;
    public Score(int scoreId, String submissionId, float totalScore, float deduction, String gradingNotes, String updatedAt) {
        this.scoreId=scoreId; this.submissionId=submissionId; this.totalScore=totalScore;
        this.deduction=deduction; this.gradingNotes=gradingNotes; this.updatedAt=updatedAt;
    }
}
""",
    "AnalysisResult.java": """package com.group1.model;
public class AnalysisResult {
    public int analysisId; public String submissionId; public String astJson;
    public String aiFeedback; public int executionTimeMs;
    public AnalysisResult(int analysisId, String submissionId, String astJson, String aiFeedback, int executionTimeMs) {
        this.analysisId=analysisId; this.submissionId=submissionId; this.astJson=astJson;
        this.aiFeedback=aiFeedback; this.executionTimeMs=executionTimeMs;
    }
}
""",
    "RuleViolation.java": """package com.group1.model;
public class RuleViolation {
    public int violationId; public int analysisId; public int ruleId;
    public String fileName; public int lineNumber; public String suggestedFix;
    public RuleViolation(int violationId, int analysisId, int ruleId, String fileName, int lineNumber, String suggestedFix) {
        this.violationId=violationId; this.analysisId=analysisId; this.ruleId=ruleId;
        this.fileName=fileName; this.lineNumber=lineNumber; this.suggestedFix=suggestedFix;
    }
}
"""
}

for name, content in models.items():
    with open(os.path.join(models_dir, name), "w", encoding="utf-8") as f:
        f.write(content)

# 2. DAOs
daos = {
    "UsersDAO.java": """package com.group1.dao;
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
""",
    "RuleDefinitionDAO.java": """package com.group1.dao;
import com.group1.model.RuleDefinition;
import java.sql.*;
public class RuleDefinitionDAO {
    private Connection conn;
    public RuleDefinitionDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RuleDefinition' and xtype='U') " +
                      "CREATE TABLE RuleDefinition (rule_id INT PRIMARY KEY, rule_code VARCHAR(50), rule_name NVARCHAR(200), severity VARCHAR(50), penalty_points FLOAT, description NVARCHAR(MAX))");
        } catch(Exception e){}
    }
    public void insert(RuleDefinition r) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO RuleDefinition VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, r.ruleId); ps.setString(2, r.ruleCode); ps.setString(3, r.ruleName);
            ps.setString(4, r.severity); ps.setFloat(5, r.penaltyPoints); ps.setString(6, r.description);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
""",
    "SubmissionDAO.java": """package com.group1.dao;
import com.group1.model.Submission;
import java.sql.*;
public class SubmissionDAO {
    private Connection conn;
    public SubmissionDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Submission' and xtype='U') " +
                      "CREATE TABLE Submission (submission_id VARCHAR(50) PRIMARY KEY, user_id VARCHAR(50), file_url VARCHAR(MAX), file_hash VARCHAR(100), status VARCHAR(50), submitted_at DATETIME)");
        } catch(Exception e){}
    }
    public void insert(Submission sub) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Submission VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, sub.submissionId); ps.setString(2, sub.userId); ps.setString(3, sub.fileUrl);
            ps.setString(4, sub.fileHash); ps.setString(5, sub.status); ps.setString(6, sub.submittedAt);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
""",
    "ScoreDAO.java": """package com.group1.dao;
import com.group1.model.Score;
import java.sql.*;
public class ScoreDAO {
    private Connection conn;
    public ScoreDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Score' and xtype='U') " +
                      "CREATE TABLE Score (score_id INT PRIMARY KEY, submission_id VARCHAR(50), total_score FLOAT, deduction FLOAT, grading_notes NVARCHAR(MAX), updated_at DATETIME)");
        } catch(Exception e){}
    }
    public void insert(Score sc) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO Score VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, sc.scoreId); ps.setString(2, sc.submissionId); ps.setFloat(3, sc.totalScore);
            ps.setFloat(4, sc.deduction); ps.setString(5, sc.gradingNotes); ps.setString(6, sc.updatedAt);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
""",
    "AnalysisResultDAO.java": """package com.group1.dao;
import com.group1.model.AnalysisResult;
import java.sql.*;
public class AnalysisResultDAO {
    private Connection conn;
    public AnalysisResultDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='AnalysisResult' and xtype='U') " +
                      "CREATE TABLE AnalysisResult (analysis_id INT PRIMARY KEY, submission_id VARCHAR(50), ast_json NVARCHAR(MAX), ai_feedback NVARCHAR(MAX), execution_time_ms INT)");
        } catch(Exception e){}
    }
    public void insert(AnalysisResult ar) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO AnalysisResult VALUES (?,?,?,?,?)")) {
            ps.setInt(1, ar.analysisId); ps.setString(2, ar.submissionId); ps.setString(3, ar.astJson);
            ps.setString(4, ar.aiFeedback); ps.setInt(5, ar.executionTimeMs);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
""",
    "RuleViolationDAO.java": """package com.group1.dao;
import com.group1.model.RuleViolation;
import java.sql.*;
public class RuleViolationDAO {
    private Connection conn;
    public RuleViolationDAO(Connection c) { this.conn = c; createTable(); }
    private void createTable() {
        try (Statement s = conn.createStatement()) {
            s.execute("IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RuleViolation' and xtype='U') " +
                      "CREATE TABLE RuleViolation (violation_id INT PRIMARY KEY, analysis_id INT, rule_id INT, file_name VARCHAR(200), line_number INT, suggested_fix NVARCHAR(MAX))");
        } catch(Exception e){}
    }
    public void insert(RuleViolation rv) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO RuleViolation VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, rv.violationId); ps.setInt(2, rv.analysisId); ps.setInt(3, rv.ruleId);
            ps.setString(4, rv.fileName); ps.setInt(5, rv.lineNumber); ps.setString(6, rv.suggestedFix);
            ps.executeUpdate();
        } catch(Exception e){ e.printStackTrace(); }
    }
}
"""
}

for name, content in daos.items():
    with open(os.path.join(daos_dir, name), "w", encoding="utf-8") as f:
        f.write(content)

print("Models and DAOs generated successfully.")
