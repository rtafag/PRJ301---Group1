import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const identifierPattern = /^[A-Za-z_$][A-Za-z0-9_$]*$/;

export async function runJavaInSandbox(sourceCode, fileName) {
  if (process.env.SANDBOX_ENABLED !== 'true') {
    return { available: false, executed: false, message: 'Sandbox execution is disabled.' };
  }

  const safeFileName = path.basename(fileName);
  const className = safeFileName.replace(/\.java$/i, '');
  if (!identifierPattern.test(className) || !new RegExp(`\\bclass\\s+${className}\\b`).test(sourceCode)) {
    return { available: true, executed: false, message: 'Upload one Java file containing a class that matches its filename.' };
  }

  const packageName = sourceCode.match(/^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;/m)?.[1] ?? '';
  const qualifiedClassName = packageName ? `${packageName}.${className}` : className;
  const temporaryDirectory = await fs.mkdtemp(path.join(os.tmpdir(), `aita-${randomUUID()}-`));
  const sourcePath = path.join(temporaryDirectory, safeFileName);
  await fs.writeFile(sourcePath, sourceCode, { encoding: 'utf8', mode: 0o444 });

  const image = process.env.SANDBOX_IMAGE || 'eclipse-temurin:8-jdk';
  const script = `mkdir -p /tmp/classes && javac -proc:none -d /tmp/classes /workspace/${safeFileName} && java -Xmx64m -cp /tmp/classes ${qualifiedClassName}`;
  const args = [
    'run', '--rm', '--pull=never', '--network=none',
    '--memory=128m', '--memory-swap=128m', '--cpus=0.5', '--pids-limit=32',
    '--read-only', '--cap-drop=ALL', '--security-opt=no-new-privileges', '--user=10001:10001',
    '--tmpfs=/tmp:rw,nosuid,size=32m',
    '--mount', `type=bind,source=${temporaryDirectory},target=/workspace,readonly`,
    '--workdir=/workspace', '--entrypoint=/bin/sh', image, '-c', script
  ];

  try {
    const result = await new Promise((resolve) => {
      const child = spawn('docker', args, { windowsHide: true });
      let stdout = '';
      let stderr = '';
      let timedOut = false;
      const timeout = setTimeout(() => {
        timedOut = true;
        child.kill('SIGKILL');
      }, 12000);
      child.stdout.on('data', (chunk) => { stdout = (stdout + chunk.toString()).slice(-16000); });
      child.stderr.on('data', (chunk) => { stderr = (stderr + chunk.toString()).slice(-16000); });
      child.on('error', (error) => {
        clearTimeout(timeout);
        resolve({ error, stdout, stderr, timedOut });
      });
      child.on('close', (code) => {
        clearTimeout(timeout);
        resolve({ code, stdout, stderr, timedOut });
      });
    });

    if (result.error?.code === 'ENOENT') {
      return { available: false, executed: false, message: 'Docker is not installed or not available on PATH.' };
    }
    return {
      available: true,
      executed: !result.error,
      timedOut: result.timedOut,
      exitCode: result.code ?? null,
      stdout: result.stdout,
      stderr: result.stderr
    };
  } finally {
    await fs.rm(temporaryDirectory, { recursive: true, force: true });
  }
}