import crypto from 'node:crypto';
import { readFile } from 'node:fs/promises';

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '{}');
if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
  throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON is unavailable or incomplete.');
}

const rules = await readFile('firestore.rules', 'utf8');
const toBase64Url = value => Buffer.from(value).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const header = toBase64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
const claims = toBase64Url(JSON.stringify({
  iss: serviceAccount.client_email,
  scope: 'https://www.googleapis.com/auth/cloud-platform',
  aud: 'https://oauth2.googleapis.com/token',
  iat: now,
  exp: now + 3600,
}));
const signingInput = `${header}.${claims}`;
const signature = crypto.sign('RSA-SHA256', Buffer.from(signingInput), serviceAccount.private_key).toString('base64url');
const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    assertion: `${signingInput}.${signature}`,
  }),
});
if (!tokenResponse.ok) throw new Error(`Token request failed: ${tokenResponse.status}`);
const { access_token: accessToken } = await tokenResponse.json();
const headers = { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' };
const project = serviceAccount.project_id;
const rulesetResponse = await fetch(`https://firebaserules.googleapis.com/v1/projects/${project}/rulesets`, {
  method: 'POST',
  headers,
  body: JSON.stringify({ source: { files: [{ name: 'firestore.rules', content: rules }] } }),
});
const rulesetText = await rulesetResponse.text();
if (!rulesetResponse.ok) throw new Error(`Rules compilation failed: ${rulesetResponse.status} ${rulesetText.slice(0, 500)}`);
const ruleset = JSON.parse(rulesetText);
const releaseName = `projects/${project}/releases/cloud.firestore`;
const releaseResponse = await fetch(`https://firebaserules.googleapis.com/v1/${releaseName}`, {
  method: 'PATCH',
  headers,
  body: JSON.stringify({
    release: { name: releaseName, rulesetName: ruleset.name },
    updateMask: 'rulesetName',
  }),
});
const releaseText = await releaseResponse.text();
if (!releaseResponse.ok) throw new Error(`Rules release update failed: ${releaseResponse.status} ${releaseText.slice(0, 500)}`);
console.log(`Firestore Rules published: ${JSON.parse(releaseText).name}`);
