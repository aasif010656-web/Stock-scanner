import crypto from "node:crypto";

const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
const projectId = process.env.FIREBASE_PROJECT_ID || "stock-take-9a67a";
const version = process.env.RELEASE_VERSION;
const tag = process.env.RELEASE_TAG;
const downloadUrl = process.env.RELEASE_DOWNLOAD_URL;

if (!raw || !version || !tag || !downloadUrl) {
  throw new Error("Missing release metadata or FIREBASE_SERVICE_ACCOUNT_JSON");
}

const account = JSON.parse(raw);
if (account.type !== "service_account" || !account.client_email || !account.private_key) {
  throw new Error("Invalid Firebase service-account JSON");
}

const base64Url = (value) => Buffer.from(value).toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
const now = Math.floor(Date.now() / 1000);
const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
const claim = base64Url(JSON.stringify({
  iss: account.client_email,
  scope: "https://www.googleapis.com/auth/datastore",
  aud: account.token_uri || "https://oauth2.googleapis.com/token",
  iat: now,
  exp: now + 300,
}));
const unsigned = `${header}.${claim}`;
const signer = crypto.createSign("RSA-SHA256");
signer.update(unsigned);
signer.end();
const assertion = `${unsigned}.${base64Url(signer.sign(account.private_key))}`;

const tokenResponse = await fetch(account.token_uri || "https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "content-type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  }).toString(),
});
if (!tokenResponse.ok) throw new Error(`Google OAuth token request failed: ${tokenResponse.status}`);
const { access_token: accessToken } = await tokenResponse.json();
if (!accessToken) throw new Error("Google OAuth response did not contain an access token");

const fields = {
  version: { stringValue: version },
  downloadUrl: { stringValue: downloadUrl },
  apkUrl: { stringValue: downloadUrl },
  releaseTag: { stringValue: tag },
  releaseName: { stringValue: `FHL ELECTRONICS ${tag}` },
  releasedAt: { timestampValue: new Date().toISOString() },
  source: { stringValue: "github-actions" },
};
const fieldPaths = Object.keys(fields).map((field) => `updateMask.fieldPaths=${encodeURIComponent(field)}`).join("&");
const endpoint = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/appSettings/updateInfo?${fieldPaths}`;
const writeResponse = await fetch(endpoint, {
  method: "PATCH",
  headers: {
    authorization: `Bearer ${accessToken}`,
    "content-type": "application/json",
  },
  body: JSON.stringify({ fields }),
});
if (!writeResponse.ok) {
  const detail = await writeResponse.text();
  throw new Error(`Firestore metadata update failed: ${writeResponse.status} ${detail.slice(0, 300)}`);
}
console.log(`Firestore updateInfo synchronized for ${version} (${tag})`);
