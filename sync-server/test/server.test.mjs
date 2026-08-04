import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createSyncServer } from "../server.mjs";

const token = "test-token-that-is-longer-than-24-characters";
const blob = {
  format: "kirachat-sync-encrypted",
  schemaVersion: 1,
  kdf: "sha256-chain-10000",
  salt: "c2FsdC1zYWx0LXNhbHQ=",
  nonce: "bm9uY2Utbm9uY2U=",
  ciphertext: "ZW5jcnlwdGVkLXN5bmMtc25hcHNob3Q="
};

async function fixture() {
  const dataDirectory = await fs.mkdtemp(path.join(os.tmpdir(), "kirachat-sync-"));
  const server = createSyncServer({ tokens: token, dataDirectory });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  return {
    dataDirectory,
    server,
    baseURL: `http://127.0.0.1:${address.port}`,
    close: async () => {
      await new Promise((resolve) => server.close(resolve));
      await fs.rm(dataDirectory, { recursive: true, force: true });
    }
  };
}

test("health, auth, versioning, and conflict protection", async () => {
  const app = await fixture();
  try {
    let response = await fetch(`${app.baseURL}/v1/health`);
    assert.equal(response.status, 200);
    assert.equal((await response.json()).service, "kirachat-sync");

    response = await fetch(`${app.baseURL}/v1/sync/meta`);
    assert.equal(response.status, 401);

    const headers = { authorization: `Bearer ${token}`, "content-type": "application/json" };
    response = await fetch(`${app.baseURL}/v1/sync/meta`, { headers });
    assert.equal(response.status, 404);

    const upload = { baseRevision: 0, deviceId: "device-1234", platform: "test", blob };
    response = await fetch(`${app.baseURL}/v1/sync/snapshot`, {
      method: "PUT", headers, body: JSON.stringify(upload)
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).revision, 1);

    response = await fetch(`${app.baseURL}/v1/sync/snapshot`, { headers });
    const snapshot = await response.json();
    assert.equal(snapshot.revision, 1);
    assert.deepEqual(snapshot.blob, blob);

    response = await fetch(`${app.baseURL}/v1/sync/snapshot`, {
      method: "PUT", headers, body: JSON.stringify(upload)
    });
    assert.equal(response.status, 409);
    assert.equal((await response.json()).currentRevision, 1);

    response = await fetch(`${app.baseURL}/v1/sync/snapshot`, {
      method: "PUT", headers, body: JSON.stringify({ ...upload, baseRevision: 1 })
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).revision, 2);
  } finally {
    await app.close();
  }
});

test("rejects malformed encrypted snapshots", async () => {
  const app = await fixture();
  try {
    const response = await fetch(`${app.baseURL}/v1/sync/snapshot`, {
      method: "PUT",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ baseRevision: 0, deviceId: "device-1234", platform: "test", blob: {} })
    });
    assert.equal(response.status, 400);
  } finally {
    await app.close();
  }
});
