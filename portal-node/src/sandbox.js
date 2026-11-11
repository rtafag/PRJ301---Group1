import { spawn } from 'node:child_process';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const identifierPattern = /^[A-Za-z_$][A-Za-z0-9_$]*$/;
const sourceFilePattern = /^[A-Za-z_$][A-Za-z0-9_$]*\.java$/i;
const DEFAULT_IMAGE = 'aita-java-sandbox:8';
const OUTPUT_LIMIT = 16000;

export async function runJavaInSandbox(sourceFiles, mainFileName, { spawnProcess = spawn, timeoutMs = 12000 } = {}) {
  if ((process.env.SANDBOX_ENABLED ?? 'true').toLowerCase() === 'false') {
    return { available: false, executed: false, message: 'Sandbox execution is disabled.' };
  }

  if (!Array.isArray(sourceFiles) || sourceFiles.length === 0 || sourceFiles.length > 20) {
    return { available: true, executed: false, message: 'Select between 1 and 20 Java source files.' };
  }
  if (sourceFiles.some((file) => !file || typeof file.sourceCode !== 'string'
      || typeof file.fileName !== 'string' || !sourceFilePattern.test(file.fileName))) {
    return { available: true, executed: false, message: 'Java source filenames must be valid class names ending in .java.' };
  }
  if (sourceFiles.reduce((total, file) => total + Buffer.byteLength(file.sourceCode), 0) > 512 * 1024) {
    return { available: true, executed: false, message: 'Combined Java source size must be 512 KB or smaller.' };
  }
  const fileNames = sourceFiles.map((file) => file.fileName.toLowerCase());
  if (new Set(fileNames).size !== fileNames.length) {
    return { available: true, executed: false, message: 'Java source filenames must be unique.' };
  }
  const mainFile = sourceFiles.find((file) => file.fileName === mainFileName);
  if (!mainFile) {
    return { available: true, executed: false, message: 'Choose the Java file that contains the program entry point.' };
  }

  const className = mainFile.fileName.replace(/\.java$/i, '');
  if (!identifierPattern.test(className) || !new RegExp(`\\bclass\\s+${className}\\b`).test(mainFile.sourceCode)) {
    return { available: true, executed: false, message: 'The selected entry-point file must contain a class matching its filename.' };
  }

  const packageName = mainFile.sourceCode.match(/^\s*package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;/m)?.[1] ?? '';
  const qualifiedClassName = packageName ? `${packageName}.${className}` : className;
  const temporaryDirectory = await fs.mkdtemp(path.join(os.tmpdir(), `aita-${randomUUID()}-`));
  const image = process.env.SANDBOX_IMAGE || DEFAULT_IMAGE;
  const containerName = `aita-sandbox-${randomUUID()}`;
  const script = `mkdir -p /tmp/classes && javac -proc:none -d /tmp/classes "$@" && java -Xmx64m -cp /tmp/classes ${qualifiedClassName}`;
  const args = [
    'run', '--rm', '--pull=never', '--network=none',
    '--name', containerName,
    '--memory=128m', '--memory-swap=128m', '--cpus=0.5', '--pids-limit=32',
    '--read-only', '--cap-drop=ALL', '--security-opt=no-new-privileges', '--user=10001:10001',
    '--tmpfs=/tmp:rw,nosuid,size=32m',
    '--mount', `type=bind,source=${temporaryDirectory},target=/workspace,readonly`,
    '--workdir=/workspace', '--entrypoint=/bin/sh', image, '-c', script, 'sandbox',
    ...sourceFiles.map((file) => file.fileName)
  ];

  try {
    await fs.chmod(temporaryDirectory, 0o755);
    for (const file of sourceFiles) {
      await fs.writeFile(path.join(temporaryDirectory, file.fileName), file.sourceCode, { encoding: 'utf8', mode: 0o444 });
    }
    const result = await runDocker(args, {
      spawnProcess,
      containerName,
      timeoutMs
    });

    if (result.error?.code === 'ENOENT') {
      return {
        available: false,
        executed: false,
        message: 'Docker CLI was not found. Install Docker Desktop, start it, and reopen the terminal.'
      };
    }
    if (result.error) {
      return { available: false, executed: false, message: `Unable to start Docker: ${result.error.message}` };
    }

    const dockerUnavailable = result.code === 125;
    const missingImage = /no such image|pull access denied|image .* not found/i.test(result.stderr);
    const daemonUnavailable = /cannot connect to the docker daemon|failed to connect to the docker api|error during connect|is the docker daemon running/i
      .test(result.stderr);
    const available = !dockerUnavailable && !missingImage && !daemonUnavailable;
    const message = missingImage
      ? `Sandbox image "${image}" is not available locally. Build it with "docker build -t ${image} -f docker\\sandbox\\Dockerfile docker\\sandbox" from the repository root.`
      : daemonUnavailable
        ? 'Docker Desktop is installed but its Linux container engine is not running. Start Docker Desktop and retry.'
        : dockerUnavailable
          ? 'Docker could not start the sandbox container. Check Docker Desktop and the image configuration.'
          : undefined;

    return {
      available,
      executed: available && result.code !== 126 && result.code !== 127,
      timedOut: result.timedOut,
      exitCode: result.code ?? null,
      stdout: result.stdout,
      stderr: result.stderr,
      ...(message ? { message } : {})
    };
  } finally {
    await fs.rm(temporaryDirectory, { recursive: true, force: true });
  }
}

function runDocker(args, { spawnProcess, containerName, timeoutMs }) {
  return new Promise((resolve) => {
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    let settled = false;
    let timeout;
    const child = spawnProcess('docker', args, {
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe']
    });

    const finish = (result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      resolve({ stdout, stderr, timedOut, ...result });
    };

    child.stdout.on('data', (chunk) => {
      stdout = (stdout + chunk.toString()).slice(-OUTPUT_LIMIT);
    });
    child.stderr.on('data', (chunk) => {
      stderr = (stderr + chunk.toString()).slice(-OUTPUT_LIMIT);
    });
    child.once('error', (error) => finish({ error }));
    child.once('close', (code) => finish({ code }));

    timeout = setTimeout(() => {
      if (settled) return;
      timedOut = true;
      const killer = spawnProcess('docker', ['kill', containerName], {
        windowsHide: true,
        stdio: ['ignore', 'ignore', 'pipe']
      });
      killer.stderr.on('data', (chunk) => {
        stderr = (stderr + chunk.toString()).slice(-OUTPUT_LIMIT);
      });
      killer.once('error', (error) => {
        stderr = (stderr + `\nUnable to stop timed-out sandbox container: ${error.message}`).slice(-OUTPUT_LIMIT);
        child.kill();
      });
      killer.once('close', (code) => {
        if (code !== 0) {
          stderr = (stderr + '\nDocker could not confirm sandbox container termination.').slice(-OUTPUT_LIMIT);
        }
        child.kill();
      });
    }, timeoutMs);
  });
}