import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import { access, readFile } from 'node:fs/promises';
import test from 'node:test';
import { runJavaInSandbox } from '../src/sandbox.js';

function createChild({ code = 0, stdout = '', stderr = '', waitForKill = false } = {}) {
  const child = new EventEmitter();
  child.stdout = new PassThrough();
  child.stderr = new PassThrough();
  child.kill = () => {
    queueMicrotask(() => {
      child.stdout.end();
      child.stderr.end();
      child.emit('close', null, 'SIGKILL');
    });
    return true;
  };
  if (!waitForKill) {
    queueMicrotask(() => {
      child.stdout.end(stdout);
      child.stderr.end(stderr);
      child.emit('close', code, null);
    });
  }
  return child;
}

function setSandboxEnabled(context, image = 'aita-java-sandbox:8') {
  const previousEnabled = process.env.SANDBOX_ENABLED;
  const previousImage = process.env.SANDBOX_IMAGE;
  process.env.SANDBOX_ENABLED = 'true';
  process.env.SANDBOX_IMAGE = image;
  context.after(() => {
    if (previousEnabled === undefined) delete process.env.SANDBOX_ENABLED;
    else process.env.SANDBOX_ENABLED = previousEnabled;
    if (previousImage === undefined) delete process.env.SANDBOX_IMAGE;
    else process.env.SANDBOX_IMAGE = previousImage;
  });
}

test('sandbox is disabled by default without launching Docker', async (context) => {
  const previousEnabled = process.env.SANDBOX_ENABLED;
  delete process.env.SANDBOX_ENABLED;
  context.after(() => {
    if (previousEnabled === undefined) delete process.env.SANDBOX_ENABLED;
    else process.env.SANDBOX_ENABLED = previousEnabled;
  });

  const result = await runJavaInSandbox('public class Demo {}', 'Demo.java', {
    spawnProcess: () => assert.fail('Docker must not start when sandbox is disabled.')
  });

  assert.equal(result.available, false);
  assert.equal(result.executed, false);
  assert.match(result.message, /disabled/i);
});

test('sandbox validates source class and filename before launching Docker', async (context) => {
  setSandboxEnabled(context);

  const result = await runJavaInSandbox('public class Other {}', 'Demo.java', {
    spawnProcess: () => assert.fail('Invalid source must not start Docker.')
  });

  assert.equal(result.executed, false);
  assert.match(result.message, /class that matches its filename/i);
});

test('sandbox starts Java with isolation limits and a read-only source mount', async (context) => {
  setSandboxEnabled(context);
  const calls = [];
  let temporarySource;
  const result = await runJavaInSandbox(
    'package demo; public class Demo { public static void main(String[] args) {} }',
    'Demo.java',
    {
      spawnProcess: (command, args, options) => {
        calls.push({ command, args, options });
        const mount = args.find((argument) => argument.startsWith('type=bind,source='));
        const directory = mount.slice('type=bind,source='.length).split(',target=')[0];
        temporarySource = `${directory}\\Demo.java`;
        return createChild({ code: 0, stdout: 'Sandbox passed.\n' });
      }
    }
  );

  assert.equal(calls.length, 1);
  assert.equal(calls[0].command, 'docker');
  assert.deepEqual(calls[0].args.slice(0, 4), ['run', '--rm', '--pull=never', '--network=none']);
  assert.ok(calls[0].args.includes('--memory=128m'));
  assert.ok(calls[0].args.includes('--cpus=0.5'));
  assert.ok(calls[0].args.includes('--pids-limit=32'));
  assert.ok(calls[0].args.includes('--read-only'));
  assert.ok(calls[0].args.includes('--cap-drop=ALL'));
  assert.ok(calls[0].args.includes('--security-opt=no-new-privileges'));
  assert.ok(calls[0].args.includes('--user=10001:10001'));
  assert.match(calls[0].args.at(-1), /java -Xmx64m -cp \/tmp\/classes demo\.Demo$/);
  assert.equal(result.available, true);
  assert.equal(result.executed, true);
  assert.equal(result.exitCode, 0);
  assert.match(result.stdout, /Sandbox passed/);
  await assert.rejects(access(temporarySource), { code: 'ENOENT' });
});

test('sandbox reports a missing image with the build command', async (context) => {
  setSandboxEnabled(context);

  const result = await runJavaInSandbox('public class Demo {}', 'Demo.java', {
    spawnProcess: () => createChild({
      code: 125,
      stderr: 'docker: pull access denied for aita-java-sandbox'
    })
  });

  assert.equal(result.available, false);
  assert.equal(result.executed, false);
  assert.match(result.message, /docker build -t aita-java-sandbox:8/);
});

test('sandbox explains when Docker Desktop is not running', async (context) => {
  setSandboxEnabled(context);

  const result = await runJavaInSandbox('public class Demo {}', 'Demo.java', {
    spawnProcess: () => createChild({
      code: 125,
      stderr: 'failed to connect to the docker API: Docker Desktop engine is unavailable'
    })
  });

  assert.equal(result.available, false);
  assert.equal(result.executed, false);
  assert.match(result.message, /Docker Desktop.*engine is not running/i);
});

test('sandbox explains when the Docker CLI is missing', async (context) => {
  setSandboxEnabled(context);

  const result = await runJavaInSandbox('public class Demo {}', 'Demo.java', {
    spawnProcess: () => {
      const child = createChild({ waitForKill: true });
      queueMicrotask(() => {
        child.emit('error', Object.assign(new Error('spawn docker ENOENT'), { code: 'ENOENT' }));
      });
      return child;
    }
  });

  assert.equal(result.available, false);
  assert.equal(result.executed, false);
  assert.match(result.message, /Docker CLI was not found/i);
});

test('sandbox kills the named container when execution times out', async (context) => {
  setSandboxEnabled(context);
  const calls = [];
  const result = await runJavaInSandbox('public class Demo {}', 'Demo.java', {
    timeoutMs: 10,
    spawnProcess: (command, args) => {
      calls.push(args);
      return args[0] === 'kill'
        ? createChild({ code: 0 })
        : createChild({ waitForKill: true });
    }
  });

  assert.equal(calls.length, 2);
  assert.equal(calls[1][0], 'kill');
  assert.match(calls[1][1], /^aita-sandbox-/);
  assert.equal(result.timedOut, true);
  assert.equal(result.exitCode, null);
});
