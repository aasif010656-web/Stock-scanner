import { cp, mkdir, rm, stat } from 'node:fs/promises';
import { join, resolve } from 'node:path';

const projectRoot = resolve(new URL('..', import.meta.url).pathname);
const sourceDir = join(projectRoot, 'app', 'src', 'main', 'assets', 'public');
const destinationDir = join(projectRoot, 'www');

try {
  const source = await stat(sourceDir);
  if (!source.isDirectory()) throw new Error('Shared source is not a directory.');
  await rm(destinationDir, { recursive: true, force: true });
  await mkdir(destinationDir, { recursive: true });
  await cp(sourceDir, destinationDir, { recursive: true });
  console.log(`Copied shared web assets from ${sourceDir} to ${destinationDir}.`);
} catch (error) {
  console.error(`Unable to synchronize shared web assets: ${error.message}`);
  process.exitCode = 1;
}
