import path from 'node:path';
import sql from 'mssql';

const tableName = 'dbo.PortalRuntimeState';

function readConfig() {
  const user = process.env.DB_USER;
  const password = process.env.DB_PASSWORD;
  if (!user || !password) {
    throw new Error('Set DB_USER and DB_PASSWORD in portal-node/.env to use SQL Server storage.');
  }

  return {
    server: process.env.DB_SERVER || 'localhost',
    port: Number(process.env.DB_PORT) || 1433,
    database: process.env.DB_NAME || 'AITA_DB',
    user,
    password,
    options: {
      encrypt: process.env.DB_ENCRYPT === 'true',
      trustServerCertificate: process.env.DB_TRUST_SERVER_CERTIFICATE !== 'false'
    }
  };
}

export async function createSqlRuntimeRepository() {
  const pool = await new sql.ConnectionPool(readConfig()).connect();

  // Ensure PortalRuntimeState table exists for supplemental UI state
  await pool.request().query(`
    IF OBJECT_ID(N'${tableName}', N'U') IS NULL
    BEGIN
      CREATE TABLE ${tableName} (
        StateId tinyint NOT NULL PRIMARY KEY CHECK (StateId = 1),
        RuntimeJson nvarchar(max) NOT NULL,
        UpdatedAt datetime2(3) NOT NULL
      );
    END;
  `);

  return {
    async load() {
      // 1. Load supplement UI state if available
      const stateResult = await pool.request().query(
        `SELECT RuntimeJson FROM ${tableName} WHERE StateId = 1;`
      );
      const runtimeJson = stateResult.recordset[0]?.RuntimeJson;
      const supplemental = runtimeJson ? JSON.parse(runtimeJson) : {};

      // 2. Load RuleDefinition
      const rulesResult = await pool.request().query(`
        SELECT rule_id, rule_code, rule_name, severity, penalty_points, description
        FROM RuleDefinition
        ORDER BY rule_id ASC
      `);
      const rules = rulesResult.recordset.map((r) => ({
        ruleId: Number(r.rule_id),
        ruleCode: r.rule_code,
        ruleName: r.rule_name,
        severity: r.severity,
        penaltyPoints: Number(r.penalty_points || 0),
        description: r.description || '',
        enabled: true
      }));

      // 3. Load RuleViolation
      const violResult = await pool.request().query(`
        SELECT violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix
        FROM RuleViolation
        ORDER BY violation_id ASC
      `);
      const violationsByAnalysis = new Map();
      for (const v of violResult.recordset) {
        const list = violationsByAnalysis.get(v.analysis_id) ?? [];
        list.push({
          ruleId: Number(v.rule_id),
          fileName: v.file_name,
          lineNumber: Number(v.line_number),
          suggestedFix: v.suggested_fix || ''
        });
        violationsByAnalysis.set(v.analysis_id, list);
      }

      // 4. Load Submission, Score, AnalysisResult
      const subResult = await pool.request().query(`
        SELECT 
          s.submission_id, s.user_id, s.file_url, s.file_hash, s.status, s.submitted_at,
          sc.score_id, sc.total_score, sc.deduction, sc.grading_notes, sc.updated_at AS score_updated_at,
          ar.analysis_id, ar.ast_json, ar.ai_feedback, ar.execution_time_ms
        FROM Submission s
        LEFT JOIN Score sc ON s.submission_id = sc.submission_id
        LEFT JOIN AnalysisResult ar ON s.submission_id = ar.submission_id
        ORDER BY s.submitted_at DESC
      `);

      const submissions = subResult.recordset.map((row) => {
        const score = row.total_score != null ? {
          totalScore: Number(row.total_score),
          deduction: Number(row.deduction || 0),
          gradingNotes: row.grading_notes || '',
          updatedAt: row.score_updated_at ? new Date(row.score_updated_at).toISOString() : null,
          manuallyEdited: false
        } : null;

        const analysis = row.analysis_id != null ? {
          astJson: row.ast_json || '',
          aiFeedback: row.ai_feedback || '',
          executionTimeMs: Number(row.execution_time_ms || 0),
          violations: violationsByAnalysis.get(row.analysis_id) ?? []
        } : null;

        return {
          submissionId: row.submission_id,
          userId: row.user_id,
          fileName: row.file_url ? path.basename(row.file_url) : `${row.submission_id}.java`,
          fileUrl: row.file_url,
          fileHash: row.file_hash,
          status: row.status,
          submittedAt: row.submitted_at ? new Date(row.submitted_at).toISOString() : new Date().toISOString(),
          sourceCode: null,
          score,
          analysis,
          manualFeedback: ''
        };
      });

      return {
        ...supplemental,
        submissions: submissions.length > 0 ? submissions : supplemental.submissions,
        rules: rules.length > 0 ? rules : supplemental.rules
      };
    },

    async save(runtimeData) {
      if (!runtimeData) return;

      // 1. Users updates (profileOverrides, passwordOverrides)
      const userUpdates = [];
      const profileOverrides = runtimeData.profileOverrides || {};
      const passwordOverrides = runtimeData.passwordOverrides || {};
      const userIds = new Set([...Object.keys(profileOverrides), ...Object.keys(passwordOverrides)]);
      for (const uid of userIds) {
        userUpdates.push({
          userId: uid,
          fullName: profileOverrides[uid]?.fullName || null,
          email: profileOverrides[uid]?.email || null,
          passwordHash: passwordOverrides[uid] || null
        });
      }

      if (userUpdates.length > 0) {
        await pool.request()
          .input('userUpdatesJson', sql.NVarChar(sql.MAX), JSON.stringify(userUpdates))
          .query(`
            UPDATE u
            SET 
              full_name = COALESCE(j.fullName, u.full_name),
              email = COALESCE(j.email, u.email),
              password_hash = COALESCE(j.passwordHash, u.password_hash)
            FROM Users u
            JOIN OPENJSON(@userUpdatesJson) WITH (
              userId varchar(50) '$.userId',
              fullName nvarchar(100) '$.fullName',
              email varchar(100) '$.email',
              passwordHash varchar(255) '$.passwordHash'
            ) j ON u.user_id = j.userId;
          `);
      }

      // 2. Rules (RuleDefinition)
      if (Array.isArray(runtimeData.rules) && runtimeData.rules.length > 0) {
        const rulesData = runtimeData.rules.map((r) => ({
          ruleId: Number(r.ruleId),
          ruleCode: r.ruleCode,
          ruleName: r.ruleName,
          severity: r.severity,
          penaltyPoints: Number(r.penaltyPoints || 0),
          description: r.description || ''
        }));

        await pool.request()
          .input('rulesJson', sql.NVarChar(sql.MAX), JSON.stringify(rulesData))
          .query(`
            MERGE RuleDefinition WITH (HOLDLOCK) AS target
            USING (
              SELECT rule_id, rule_code, rule_name, severity, penalty_points, description
              FROM OPENJSON(@rulesJson) WITH (
                rule_id int '$.ruleId',
                rule_code varchar(50) '$.ruleCode',
                rule_name nvarchar(200) '$.ruleName',
                severity varchar(50) '$.severity',
                penalty_points float '$.penaltyPoints',
                description nvarchar(max) '$.description'
              )
            ) AS source
            ON target.rule_id = source.rule_id
            WHEN MATCHED THEN
              UPDATE SET rule_code = source.rule_code, rule_name = source.rule_name, severity = source.severity, penalty_points = source.penalty_points, description = source.description
            WHEN NOT MATCHED THEN
              INSERT (rule_id, rule_code, rule_name, severity, penalty_points, description)
              VALUES (source.rule_id, source.rule_code, source.rule_name, source.severity, source.penalty_points, source.description);
          `);
      }

      // 3. Submissions, Scores, AnalysisResult, RuleViolation
      if (Array.isArray(runtimeData.submissions) && runtimeData.submissions.length > 0) {
        // Deduplicate submissions by submissionId (last-write-wins)
        const uniqueSubsMap = new Map();
        for (const s of runtimeData.submissions) {
          if (s && s.submissionId) {
            uniqueSubsMap.set(s.submissionId, s);
          }
        }
        const uniqueSubmissions = Array.from(uniqueSubsMap.values());

        const subsData = [];
        const scoresData = [];
        const analysesData = [];
        const violationsData = [];

        for (const s of uniqueSubmissions) {
          subsData.push({
            submissionId: s.submissionId,
            userId: s.userId,
            fileUrl: s.fileUrl || '',
            fileHash: s.fileHash || '',
            status: s.status || 'Submitted',
            submittedAt: s.submittedAt ? new Date(s.submittedAt).toISOString() : new Date().toISOString()
          });

          if (s.score && s.score.totalScore != null) {
            scoresData.push({
              submissionId: s.submissionId,
              totalScore: Number(s.score.totalScore),
              deduction: Number(s.score.deduction || 0),
              gradingNotes: s.score.gradingNotes || '',
              updatedAt: s.score.updatedAt ? new Date(s.score.updatedAt).toISOString() : new Date().toISOString()
            });
          }

          if (s.analysis) {
            analysesData.push({
              submissionId: s.submissionId,
              astJson: typeof s.analysis.astJson === 'string' ? s.analysis.astJson : JSON.stringify(s.analysis.astJson || {}),
              aiFeedback: s.analysis.aiFeedback || '',
              executionTimeMs: Number(s.analysis.executionTimeMs || 0)
            });

            if (Array.isArray(s.analysis.violations)) {
              for (const v of s.analysis.violations) {
                violationsData.push({
                  submissionId: s.submissionId,
                  ruleId: Number(v.ruleId),
                  fileName: v.fileName || s.fileName || 'Main.java',
                  lineNumber: Number(v.lineNumber || 1),
                  suggestedFix: v.suggestedFix || ''
                });
              }
            }
          }
        }

        // Upsert Submission
        await pool.request()
          .input('subsJson', sql.NVarChar(sql.MAX), JSON.stringify(subsData))
          .query(`
            MERGE Submission WITH (HOLDLOCK) AS target
            USING (
              SELECT submission_id, user_id, file_url, file_hash, status, submitted_at
              FROM OPENJSON(@subsJson) WITH (
                submission_id varchar(50) '$.submissionId',
                user_id varchar(50) '$.userId',
                file_url varchar(max) '$.fileUrl',
                file_hash varchar(100) '$.fileHash',
                status varchar(50) '$.status',
                submitted_at datetime '$.submittedAt'
              )
            ) AS source
            ON target.submission_id = source.submission_id
            WHEN MATCHED THEN
              UPDATE SET user_id = source.user_id, file_url = source.file_url, file_hash = source.file_hash, status = source.status, submitted_at = source.submitted_at
            WHEN NOT MATCHED THEN
              INSERT (submission_id, user_id, file_url, file_hash, status, submitted_at)
              VALUES (source.submission_id, source.user_id, source.file_url, source.file_hash, source.status, source.submitted_at);
          `);

        // Upsert Score
        if (scoresData.length > 0) {
          await pool.request()
            .input('scoresJson', sql.NVarChar(sql.MAX), JSON.stringify(scoresData))
            .query(`
              DECLARE @maxScoreId INT = (SELECT ISNULL(MAX(score_id), 0) FROM Score);
              
              MERGE Score WITH (HOLDLOCK) AS target
              USING (
                SELECT 
                  COALESCE(sc.score_id, @maxScoreId + ROW_NUMBER() OVER (ORDER BY j.submissionId)) AS score_id,
                  j.submissionId AS submission_id,
                  j.totalScore AS total_score,
                  j.deduction AS deduction,
                  j.gradingNotes AS grading_notes,
                  j.updatedAt AS updated_at
                FROM OPENJSON(@scoresJson) WITH (
                  submissionId varchar(50) '$.submissionId',
                  totalScore float '$.totalScore',
                  deduction float '$.deduction',
                  gradingNotes nvarchar(max) '$.gradingNotes',
                  updatedAt datetime '$.updatedAt'
                ) j
                LEFT JOIN Score sc ON sc.submission_id = j.submissionId
              ) AS source
              ON target.submission_id = source.submission_id
              WHEN MATCHED THEN
                UPDATE SET total_score = source.total_score, deduction = source.deduction, grading_notes = source.grading_notes, updated_at = source.updated_at
              WHEN NOT MATCHED THEN
                INSERT (score_id, submission_id, total_score, deduction, grading_notes, updated_at)
                VALUES (source.score_id, source.submission_id, source.total_score, source.deduction, source.grading_notes, source.updated_at);
            `);
        }

        // Upsert AnalysisResult
        if (analysesData.length > 0) {
          await pool.request()
            .input('analysesJson', sql.NVarChar(sql.MAX), JSON.stringify(analysesData))
            .query(`
              DECLARE @maxAnalysisId INT = (SELECT ISNULL(MAX(analysis_id), 0) FROM AnalysisResult);

              MERGE AnalysisResult WITH (HOLDLOCK) AS target
              USING (
                SELECT 
                  COALESCE(ar.analysis_id, @maxAnalysisId + ROW_NUMBER() OVER (ORDER BY j.submissionId)) AS analysis_id,
                  j.submissionId AS submission_id,
                  j.astJson AS ast_json,
                  j.aiFeedback AS ai_feedback,
                  j.executionTimeMs AS execution_time_ms
                FROM OPENJSON(@analysesJson) WITH (
                  submissionId varchar(50) '$.submissionId',
                  astJson nvarchar(max) '$.astJson',
                  aiFeedback nvarchar(max) '$.aiFeedback',
                  executionTimeMs int '$.executionTimeMs'
                ) j
                LEFT JOIN AnalysisResult ar ON ar.submission_id = j.submissionId
              ) AS source
              ON target.submission_id = source.submission_id
              WHEN MATCHED THEN
                UPDATE SET ast_json = source.ast_json, ai_feedback = source.ai_feedback, execution_time_ms = source.execution_time_ms
              WHEN NOT MATCHED THEN
                INSERT (analysis_id, submission_id, ast_json, ai_feedback, execution_time_ms)
                VALUES (source.analysis_id, source.submission_id, source.ast_json, source.ai_feedback, source.execution_time_ms);
            `);
        }

        // Sync RuleViolation (Delete old for modified submissions, then insert new)
        if (violationsData.length > 0) {
          await pool.request()
            .input('violationsJson', sql.NVarChar(sql.MAX), JSON.stringify(violationsData))
            .query(`
              DELETE rv
              FROM RuleViolation rv
              JOIN AnalysisResult ar ON ar.analysis_id = rv.analysis_id
              WHERE ar.submission_id IN (
                SELECT DISTINCT submissionId FROM OPENJSON(@violationsJson) WITH (submissionId varchar(50) '$.submissionId')
              );

              DECLARE @maxViolId INT = (SELECT ISNULL(MAX(violation_id), 0) FROM RuleViolation);

              INSERT INTO RuleViolation (violation_id, analysis_id, rule_id, file_name, line_number, suggested_fix)
              SELECT 
                @maxViolId + ROW_NUMBER() OVER (ORDER BY j.submissionId, j.ruleId, j.lineNumber) AS violation_id,
                ar.analysis_id,
                j.ruleId,
                j.fileName,
                j.lineNumber,
                j.suggestedFix
              FROM OPENJSON(@violationsJson) WITH (
                submissionId varchar(50) '$.submissionId',
                ruleId int '$.ruleId',
                fileName varchar(200) '$.fileName',
                lineNumber int '$.lineNumber',
                suggestedFix nvarchar(max) '$.suggestedFix'
              ) j
              JOIN AnalysisResult ar ON ar.submission_id = j.submissionId;
            `);
        }
      }

      // 4. Update PortalRuntimeState for UI metadata
      await pool.request()
        .input('runtimeJson', sql.NVarChar(sql.MAX), JSON.stringify(runtimeData))
        .query(`
          MERGE ${tableName} WITH (HOLDLOCK) AS target
          USING (SELECT CAST(1 AS tinyint) AS StateId, @runtimeJson AS RuntimeJson) AS source
          ON target.StateId = source.StateId
          WHEN MATCHED THEN UPDATE SET RuntimeJson = source.RuntimeJson, UpdatedAt = SYSUTCDATETIME()
          WHEN NOT MATCHED THEN INSERT (StateId, RuntimeJson, UpdatedAt)
            VALUES (source.StateId, source.RuntimeJson, SYSUTCDATETIME());
        `);
    },

    close() {
      return pool.close();
    }
  };
}
