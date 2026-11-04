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
      const result = await pool.request().query(
        `SELECT RuntimeJson FROM ${tableName} WHERE StateId = 1;`
      );
      const runtimeJson = result.recordset[0]?.RuntimeJson;
      return runtimeJson ? JSON.parse(runtimeJson) : null;
    },
    async save(runtimeData) {
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
