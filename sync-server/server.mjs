import crypto from "node:crypto";
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const MAX_BODY_BYTES = 256 * 1024 * 1024;
const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff"
};

function json(response, status, value) {
  response.writeHead(status, JSON_HEADERS);
  response.end(JSON.stringify(value));
}

function parseTokens(value) {
  const tokens = String(value ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  if (tokens.length === 0 || tokens.some((token) => token.length < 24)) {
    throw new Error("SYNC_TOKENS must contain one or more comma-separated tokens of at least 24 characters");
  }
  return tokens;
}

function safeEqual(left, right) {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function authorizedToken(request, tokens) {
  const authorization = String(request.headers.authorization ?? "");
  if (!authorization.startsWith("Bearer ")) return null;
  const candidate = authorization.slice(7).trim();
  return tokens.find((token) => safeEqual(candidate, token)) ?? null;
}

function slotName(token) {
  return crypto.createHash("sha256").update(token).digest("hex") + ".json";
}

async function readBody(request) {
  const declared = Number(request.headers["content-length"] ?? 0);
  if (declared > MAX_BODY_BYTES) throw Object.assign(new Error("payload_too_large"), { status: 413 });
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw Object.assign(new Error("payload_too_large"), { status: 413 });
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw Object.assign(new Error("invalid_json"), { status: 400 });
  }
}

function validateBlob(blob) {
  return blob && blob.format === "kirachat-sync-encrypted"
    && blob.schemaVersion === 1
    && blob.kdf === "sha256-chain-10000"
    && typeof blob.salt === "string" && blob.salt.length >= 20
    && typeof blob.nonce === "string" && blob.nonce.length >= 12
    && typeof blob.ciphertext === "string" && blob.ciphertext.length > 20;
}

async function readSnapshot(file) {
  try {
    return JSON.parse(await fs.readFile(file, "utf8"));
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
}

async function writeSnapshot(file, snapshot) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  const temporary = `${file}.${process.pid}.${crypto.randomUUID()}.tmp`;
  await fs.writeFile(temporary, JSON.stringify(snapshot), { mode: 0o600 });
  await fs.rename(temporary, file);
}

export function createSyncServer(options = {}) {
  const tokens = parseTokens(options.tokens ?? process.env.SYNC_TOKENS);
  const dataDirectory = path.resolve(options.dataDirectory ?? process.env.SYNC_DATA_DIR ?? "./data");

  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://localhost");
      if (request.method === "GET" && url.pathname === "/v1/health") {
        json(response, 200, { service: "kirachat-sync", schemaVersion: 1 });
        return;
      }
      if (!["/v1/sync/meta", "/v1/sync/snapshot"].includes(url.pathname)) {
        json(response, 404, { error: "not_found" });
        return;
      }

      const token = authorizedToken(request, tokens);
      if (!token) {
        response.setHeader("www-authenticate", "Bearer");
        json(response, 401, { error: "unauthorized" });
        return;
      }
      const file = path.join(dataDirectory, slotName(token));
      const current = await readSnapshot(file);

      if (request.method === "GET" && url.pathname === "/v1/sync/meta") {
        if (!current) return json(response, 404, { error: "snapshot_not_found" });
        json(response, 200, {
          schemaVersion: 1,
          revision: current.revision,
          updatedAt: current.updatedAt,
          deviceId: current.deviceId,
          platform: current.platform
        });
        return;
      }
      if (request.method === "GET" && url.pathname === "/v1/sync/snapshot") {
        if (!current) return json(response, 404, { error: "snapshot_not_found" });
        json(response, 200, current);
        return;
      }
      if (request.method === "PUT" && url.pathname === "/v1/sync/snapshot") {
        const body = await readBody(request);
        const baseRevision = Number(body.baseRevision);
        const currentRevision = Number(current?.revision ?? 0);
        if (!Number.isSafeInteger(baseRevision) || baseRevision < 0) {
          return json(response, 400, { error: "invalid_base_revision" });
        }
        if (baseRevision !== currentRevision) {
          return json(response, 409, {
            error: "revision_conflict",
            currentRevision,
            updatedAt: current?.updatedAt ?? 0
          });
        }
        if (typeof body.deviceId !== "string" || body.deviceId.length < 8
            || typeof body.platform !== "string" || !validateBlob(body.blob)) {
          return json(response, 400, { error: "invalid_snapshot" });
        }
        const snapshot = {
          schemaVersion: 1,
          revision: currentRevision + 1,
          updatedAt: Date.now(),
          deviceId: body.deviceId.slice(0, 128),
          platform: body.platform.slice(0, 32),
          blob: body.blob
        };
        await writeSnapshot(file, snapshot);
        json(response, 200, {
          schemaVersion: 1,
          revision: snapshot.revision,
          updatedAt: snapshot.updatedAt,
          deviceId: snapshot.deviceId,
          platform: snapshot.platform
        });
        return;
      }
      response.setHeader("allow", "GET, PUT");
      json(response, 405, { error: "method_not_allowed" });
    } catch (error) {
      json(response, error?.status ?? 500, {
        error: error?.message === "payload_too_large" ? "payload_too_large" : "server_error"
      });
    }
  });
}

const isMain = process.argv[1]
  && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const port = Number(process.env.PORT ?? 8788);
  const server = createSyncServer();
  server.listen(port, "0.0.0.0", () => {
    process.stdout.write(`KiraChat sync server listening on :${port}\n`);
  });
}
