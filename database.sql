-- ============================================================================
-- AITA (AI Teaching Assistant) Database Schema
-- Microsoft SQL Server 2012+ / Azure SQL
-- Target Database: AITA_DB
-- ============================================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'AITA_DB')
BEGIN
    CREATE DATABASE AITA_DB;
END;
GO

USE AITA_DB;
GO

-- 1. Drop Foreign Keys & Tables in reverse dependency order if recreating
IF OBJECT_ID(N'dbo.RuleViolation', N'U') IS NOT NULL DROP TABLE dbo.RuleViolation;
IF OBJECT_ID(N'dbo.Score', N'U') IS NOT NULL DROP TABLE dbo.Score;
IF OBJECT_ID(N'dbo.AnalysisResult', N'U') IS NOT NULL DROP TABLE dbo.AnalysisResult;
IF OBJECT_ID(N'dbo.Submission', N'U') IS NOT NULL DROP TABLE dbo.Submission;
IF OBJECT_ID(N'dbo.RuleDefinition', N'U') IS NOT NULL DROP TABLE dbo.RuleDefinition;
IF OBJECT_ID(N'dbo.Users', N'U') IS NOT NULL DROP TABLE dbo.Users;
IF OBJECT_ID(N'dbo.PortalRuntimeState', N'U') IS NOT NULL DROP TABLE dbo.PortalRuntimeState;
GO

-- 2. Create Users Table
CREATE TABLE dbo.Users (
    user_id VARCHAR(50) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name NVARCHAR(100) NULL,
    email VARCHAR(100) NULL,
    role VARCHAR(50) NOT NULL,
    created_at DATETIME NULL DEFAULT GETDATE(),
    CONSTRAINT PK_Users PRIMARY KEY CLUSTERED (user_id ASC),
    CONSTRAINT UQ_Users_Username UNIQUE (username)
);
GO

-- 3. Create RuleDefinition Table
CREATE TABLE dbo.RuleDefinition (
    rule_id INT NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    rule_name NVARCHAR(200) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    penalty_points FLOAT NOT NULL DEFAULT 0.0,
    description NVARCHAR(MAX) NULL,
    CONSTRAINT PK_RuleDefinition PRIMARY KEY CLUSTERED (rule_id ASC),
    CONSTRAINT UQ_RuleDefinition_Code UNIQUE (rule_code)
);
GO

-- 4. Create Submission Table
CREATE TABLE dbo.Submission (
    submission_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    file_url VARCHAR(MAX) NULL,
    file_hash VARCHAR(100) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'Submitted',
    submitted_at DATETIME NULL DEFAULT GETDATE(),
    CONSTRAINT PK_Submission PRIMARY KEY CLUSTERED (submission_id ASC),
    CONSTRAINT FK_Submission_Users FOREIGN KEY (user_id)
        REFERENCES dbo.Users (user_id) ON DELETE CASCADE
);
GO

-- 5. Create Score Table
CREATE TABLE dbo.Score (
    score_id INT NOT NULL,
    submission_id VARCHAR(50) NOT NULL,
    total_score FLOAT NOT NULL DEFAULT 0.0,
    deduction FLOAT NOT NULL DEFAULT 0.0,
    grading_notes NVARCHAR(MAX) NULL,
    updated_at DATETIME NULL DEFAULT GETDATE(),
    CONSTRAINT PK_Score PRIMARY KEY CLUSTERED (score_id ASC),
    CONSTRAINT UQ_Score_Submission UNIQUE (submission_id),
    CONSTRAINT FK_Score_Submission FOREIGN KEY (submission_id)
        REFERENCES dbo.Submission (submission_id) ON DELETE CASCADE
);
GO

-- 6. Create AnalysisResult Table
CREATE TABLE dbo.AnalysisResult (
    analysis_id INT NOT NULL,
    submission_id VARCHAR(50) NOT NULL,
    ast_json NVARCHAR(MAX) NULL,
    ai_feedback NVARCHAR(MAX) NULL,
    execution_time_ms INT NULL DEFAULT 0,
    CONSTRAINT PK_AnalysisResult PRIMARY KEY CLUSTERED (analysis_id ASC),
    CONSTRAINT UQ_AnalysisResult_Submission UNIQUE (submission_id),
    CONSTRAINT FK_Analysis_Submission FOREIGN KEY (submission_id)
        REFERENCES dbo.Submission (submission_id) ON DELETE CASCADE
);
GO

-- 7. Create RuleViolation Table
CREATE TABLE dbo.RuleViolation (
    violation_id INT NOT NULL,
    analysis_id INT NOT NULL,
    rule_id INT NOT NULL,
    file_name VARCHAR(200) NULL,
    line_number INT NULL,
    suggested_fix NVARCHAR(MAX) NULL,
    CONSTRAINT PK_RuleViolation PRIMARY KEY CLUSTERED (violation_id ASC),
    CONSTRAINT FK_Violation_Analysis FOREIGN KEY (analysis_id)
        REFERENCES dbo.AnalysisResult (analysis_id) ON DELETE CASCADE,
    CONSTRAINT FK_Violation_Rule FOREIGN KEY (rule_id)
        REFERENCES dbo.RuleDefinition (rule_id)
);
GO

-- 8. Create PortalRuntimeState Table (for supplemental Web state)
CREATE TABLE dbo.PortalRuntimeState (
    StateId TINYINT NOT NULL PRIMARY KEY CHECK (StateId = 1),
    RuntimeJson NVARCHAR(MAX) NOT NULL,
    UpdatedAt DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- 9. Create Performance Indexes
CREATE NONCLUSTERED INDEX IX_Submission_UserId ON dbo.Submission (user_id ASC);
CREATE NONCLUSTERED INDEX IX_Submission_FileHash ON dbo.Submission (file_hash ASC);
CREATE NONCLUSTERED INDEX IX_Submission_Status ON dbo.Submission (status ASC);
CREATE NONCLUSTERED INDEX IX_Violation_AnalysisId ON dbo.RuleViolation (analysis_id ASC);
CREATE NONCLUSTERED INDEX IX_Violation_RuleId ON dbo.RuleViolation (rule_id ASC);
CREATE NONCLUSTERED INDEX IX_Score_TotalScore ON dbo.Score (total_score ASC);
GO

-- ============================================================================
-- Default Core AST Rules
-- ============================================================================
INSERT INTO dbo.RuleDefinition (rule_id, rule_code, rule_name, severity, penalty_points, description) VALUES
(1, 'MISSING_COMMENT', N'Missing method or class comment', 'Warning', 0.5, N'Methods and classes must have clear Javadoc/comments explaining purpose.'),
(2, 'NAMING_CONVENTION', N'Naming convention violation', 'Info', 0.2, N'Variables must follow camelCase; classes must follow PascalCase.'),
(3, 'LONG_METHOD', N'Method too long (> 50 lines)', 'Warning', 1.0, N'Refactor large monolithic methods into smaller modular helpers.'),
(4, 'EMPTY_CATCH_BLOCK', N'Empty catch block', 'Error', 1.5, N'Exceptions must be handled, logged, or re-thrown; empty catch blocks hide bugs.'),
(5, 'INFINITE_LOOP', N'Potential infinite loop detected', 'Critical', 2.0, N'Loops without state modifications or termination conditions will hang execution.'),
(6, 'RESOURCE_LEAK', N'Unclosed resource detected', 'Critical', 2.0, N'Streams, connections, and readers must be enclosed in try-with-resources.');
GO
