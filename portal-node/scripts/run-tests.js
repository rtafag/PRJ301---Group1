import { readdirSync } from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const testDirectory = fileURLToPath(new URL('../test/', import.meta.url));

function findTestFiles(directory) {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) return findTestFiles(entryPath);
      return entry.isFile() && /\.test\.[cm]?js$/.test(entry.name) ? [entryPath] : [];
    });
}

const testFiles = findTestFiles(testDirectory).sort();
if (testFiles.length === 0) {
  throw new Error(`No test files were found in ${testDirectory}.`);
}

const result = spawnSync(process.execPath, ['--test', ...testFiles], { stdio: 'inherit' });
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
