import { createHash, createHmac, randomBytes, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import path from "node:path";
import {
  Contract,
  JsonRpcProvider,
  NonceManager,
  Wallet,
  getAddress,
  keccak256,
  toUtf8Bytes
} from "ethers";
import { JsonStore } from "./json-store.mjs";

const CONTRACT_ABI = [
  "function award(address account,uint256 amount,bytes32 reference)",
  "function balanceOf(address account) view returns (uint256)",
  "function issuers(address issuer) view returns (bool)",
  "function paused() view returns (bool)",
  "function processedReferences(bytes32 reference) view returns (bool)"
];

const settings = loadSettings();
const provider = new JsonRpcProvider(settings.rpcUrl);
const issuerWallet = new Wallet(settings.issuerPrivateKey, provider);
const issuer = new NonceManager(issuerWallet);
const contract = new Contract(settings.contractAddress, CONTRACT_ABI, issuer);
const store = new JsonStore(settings.dataFile);
const enrollmentLimits = new Map();
let transactionQueue = Promise.resolve();

await store.load();
await verifyChainConfiguration();

const server = createServer(async (request, response) => {
  try {
    applySecurityHeaders(response);
    if (request.method === "GET" && request.url === "/health") {
      return sendJson(response, 200, await health());
    }
    if (request.method === "POST" && request.url === "/v1/points/enroll") {
      enforceEnrollmentRate(request);
      await readJson(request);
      return sendJson(response, 201, await enroll());
    }
    if (request.method === "GET" && request.url === "/v1/points") {
      const account = authenticate(request);
      return sendJson(response, 200, await summary(account));
    }
    if (request.method === "POST" && request.url === "/v1/points/claim") {
      const account = authenticate(request);
      const body = await readJson(request);
      if (body.action && body.action !== "daily_checkin") {
        return sendError(response, 400, "unsupported_action", "不支持这个积分任务");
      }
      const result = await queueTransaction(() => claimDaily(account.tokenHash));
      return sendJson(response, result.alreadyClaimed ? 200 : 201, result);
    }
    sendError(response, 404, "not_found", "接口不存在");
  } catch (error) {
    const status = Number(error.statusCode) || 500;
    const code = error.code || "internal_error";
    if (status >= 500) console.error(error);
    sendError(
      response,
      status,
      code,
      status >= 500 ? "积分服务暂时不可用" : error.message
    );
  }
});

server.listen(settings.port, () => {
  console.log(`Chengyu points service listening on :${settings.port}`);
  console.log(`Network: ${settings.chainId} · Contract: ${settings.contractAddress}`);
  console.log(`Issuer: ${issuerWallet.address}`);
});

async function enroll() {
  const accessToken = randomBytes(32).toString("base64url");
  const tokenHash = sha256(accessToken);
  const accountId = randomUUID();
  const addressBytes = createHmac("sha256", settings.hmacSecret)
    .update(`chengyu-points:${accountId}`)
    .digest("hex")
    .slice(-40);
  const account = {
    tokenHash,
    accountId,
    address: getAddress(`0x${addressBytes}`),
    createdAt: new Date().toISOString(),
    lastClaimDate: "",
    latestTxHash: ""
  };
  await store.update((data) => {
    data.accounts[tokenHash] = account;
  });
  return {
    accessToken,
    ...(await summary(account))
  };
}

async function claimDaily(tokenHash) {
  const account = store.data.accounts[tokenHash];
  if (!account) throw httpError(401, "invalid_token", "积分账户已失效");

  const today = utcDay();
  if (account.lastClaimDate === today) {
    return {
      ...(await summary(account)),
      alreadyClaimed: true
    };
  }

  const reference = keccak256(
    toUtf8Bytes(`daily_checkin:${account.accountId}:${today}`)
  );
  let txHash = account.latestTxHash;
  const processed = await contract.processedReferences(reference);
  if (!processed) {
    const transaction = await contract.award(
      account.address,
      settings.dailyCheckinPoints,
      reference
    );
    txHash = transaction.hash;
    const receipt = await transaction.wait(1);
    if (!receipt || receipt.status !== 1) {
      throw new Error("Point award transaction was not confirmed");
    }
  }

  await store.update((data) => {
    const current = data.accounts[tokenHash];
    if (!current) return;
    current.lastClaimDate = today;
    current.latestTxHash = txHash;
  });
  return {
    ...(await summary(store.data.accounts[tokenHash])),
    awarded: settings.dailyCheckinPoints,
    alreadyClaimed: false
  };
}

async function summary(account) {
  const balance = await contract.balanceOf(account.address);
  const latestTxUrl = account.latestTxHash
    ? `${settings.explorerUrl}/tx/${account.latestTxHash}`
    : "";
  return {
    balance: balance.toString(),
    address: account.address,
    checkedInToday: account.lastClaimDate === utcDay(),
    dailyReward: settings.dailyCheckinPoints,
    network: {
      name: settings.networkName,
      chainId: settings.chainId.toString(),
      explorerUrl: settings.explorerUrl
    },
    contractAddress: settings.contractAddress,
    accountUrl: `${settings.explorerUrl}/address/${account.address}`,
    latestTxUrl
  };
}

function authenticate(request) {
  const header = request.headers.authorization || "";
  if (!header.startsWith("Bearer ")) {
    throw httpError(401, "missing_token", "请先创建积分账户");
  }
  const token = header.slice(7).trim();
  if (!token || token.length > 200) {
    throw httpError(401, "invalid_token", "积分账户已失效");
  }
  const tokenHash = sha256(token);
  const account = store.data.accounts[tokenHash];
  if (!account) throw httpError(401, "invalid_token", "积分账户已失效");
  return account;
}

function enforceEnrollmentRate(request) {
  const now = Date.now();
  const windowStart = now - 60 * 60 * 1000;
  // Never trust a client-supplied X-Forwarded-For header here. A production
  // reverse proxy should enforce its own rate limit before forwarding requests.
  const ip = request.socket.remoteAddress || "unknown";
  const recent = (enrollmentLimits.get(ip) || []).filter(
    (timestamp) => timestamp >= windowStart
  );
  if (recent.length >= 5) {
    throw httpError(429, "rate_limited", "创建积分账户过于频繁，请稍后再试");
  }
  recent.push(now);
  enrollmentLimits.set(ip, recent);
}

async function verifyChainConfiguration() {
  const network = await provider.getNetwork();
  if (network.chainId !== settings.chainId) {
    throw new Error(
      `Expected chain ${settings.chainId}, got ${network.chainId}`
    );
  }
  const code = await provider.getCode(settings.contractAddress);
  if (code === "0x") throw new Error("CONTRACT_ADDRESS has no deployed code");
  const enabled = await contract.issuers(issuerWallet.address);
  if (!enabled) throw new Error("ISSUER_PRIVATE_KEY is not an enabled issuer");
}

async function health() {
  const [blockNumber, paused] = await Promise.all([
    provider.getBlockNumber(),
    contract.paused()
  ]);
  return {
    ok: true,
    chainId: settings.chainId.toString(),
    blockNumber,
    contractAddress: settings.contractAddress,
    paused
  };
}

function queueTransaction(work) {
  const result = transactionQueue.then(work, work);
  transactionQueue = result.catch(() => {});
  return result;
}

function loadSettings() {
  const hmacSecret = required("POINTS_HMAC_SECRET");
  if (hmacSecret.length < 32) {
    throw new Error("POINTS_HMAC_SECRET must contain at least 32 characters");
  }
  const chainId = BigInt(process.env.EXPECTED_CHAIN_ID || "84532");
  const contractAddress = getAddress(required("CONTRACT_ADDRESS"));
  const dailyCheckinPoints = parsePositiveInteger(
    process.env.DAILY_CHECKIN_POINTS || "10",
    "DAILY_CHECKIN_POINTS"
  );
  return {
    port: parsePositiveInteger(process.env.PORT || "8788", "PORT"),
    rpcUrl: process.env.RPC_URL || "https://sepolia.base.org",
    chainId,
    networkName: process.env.NETWORK_NAME || "Base Sepolia",
    explorerUrl: (
      process.env.EXPLORER_URL || "https://sepolia-explorer.base.org"
    ).replace(/\/+$/, ""),
    contractAddress,
    issuerPrivateKey: required("ISSUER_PRIVATE_KEY"),
    hmacSecret,
    dailyCheckinPoints,
    dataFile: path.resolve(process.env.DATA_FILE || "./data/points.json")
  };
}

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function parsePositiveInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function utcDay() {
  return new Date().toISOString().slice(0, 10);
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function httpError(statusCode, code, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  error.code = code;
  return error;
}

async function readJson(request) {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of request) {
    bytes += chunk.length;
    if (bytes > 16 * 1024) {
      throw httpError(413, "body_too_large", "请求内容过大");
    }
    chunks.push(chunk);
  }
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw httpError(400, "invalid_json", "请求格式不正确");
  }
}

function sendError(response, status, code, message) {
  sendJson(response, status, { error: code, message });
}

function sendJson(response, status, body) {
  if (response.headersSent) return;
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store"
  });
  response.end(JSON.stringify(body));
}

function applySecurityHeaders(response) {
  response.setHeader("x-content-type-options", "nosniff");
  response.setHeader("referrer-policy", "no-referrer");
}
