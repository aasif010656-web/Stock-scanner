import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDirectory, '..', '..');
const canonicalPublicDirectory = 'FHL_NATIVE_APP/app/src/main/assets/public';
const canonicalIndex = path.join(repositoryRoot, canonicalPublicDirectory, 'index.html');
const firebaseConfigPath = path.join(repositoryRoot, 'firebase.json');

if (!fs.existsSync(canonicalIndex)) {
  throw new Error(`Canonical shared bundle is missing: ${canonicalPublicDirectory}/index.html`);
}

const firebaseConfig = JSON.parse(fs.readFileSync(firebaseConfigPath, 'utf8'));
const hosting = Array.isArray(firebaseConfig.hosting) ? firebaseConfig.hosting[0] : firebaseConfig.hosting;

if (!hosting || hosting.public !== canonicalPublicDirectory) {
  throw new Error(
    `Firebase Hosting must deploy the canonical Android bundle from ${canonicalPublicDirectory}; found ${hosting?.public ?? 'no hosting.public value'}.`,
  );
}

console.log(`Firebase sync contract verified: Android and Firebase share ${canonicalPublicDirectory}.`);
