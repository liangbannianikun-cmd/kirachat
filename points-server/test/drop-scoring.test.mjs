import assert from "node:assert/strict";
import test from "node:test";
import {
  DEFAULT_DROP_POLICY,
  allocateDrop,
  buildDropTree,
  scoreAccount
} from "../src/drop/scoring.mjs";

const ADDRESS_A = "0x00000000000000000000000000000000000000A1";
const ADDRESS_B = "0x00000000000000000000000000000000000000B2";
const ADDRESS_C = "0x00000000000000000000000000000000000000C3";

function genuine(account, overrides = {}) {
  return {
    account,
    walletSignatureVerified: true,
    accountAgeDays: 60,
    walletBoundDays: 30,
    activeDays: 5,
    meaningfulSessions: 6,
    acceptedContributions: 1,
    helpfulReports: 1,
    streakWeeks: 4,
    riskScore: 5,
    deviceAccountCount: 1,
    recentDeviceActivity: "normal",
    deviceIntegrity: "strong",
    clusterId: `device:${account}`,
    ...overrides
  };
}

test("genuine activity is scored with capped actions", () => {
  const regular = scoreAccount(genuine(ADDRESS_A));
  const spammed = scoreAccount(genuine(ADDRESS_A, {
    activeDays: 500,
    meaningfulSessions: 500,
    acceptedContributions: 500,
    helpfulReports: 500,
    streakWeeks: 500
  }));
  assert.equal(regular.eligible, true);
  assert.equal(spammed.eligible, true);
  assert.equal(spammed.score, 278);
  assert.ok(spammed.score > regular.score);
});

test("new, unsigned, hyperactive, and unverifiable accounts are rejected", () => {
  const result = scoreAccount(genuine(ADDRESS_A, {
    walletSignatureVerified: false,
    accountAgeDays: 1,
    recentDeviceActivity: "high",
    deviceIntegrity: "none"
  }));
  assert.equal(result.eligible, false);
  assert.ok(result.reasons.includes("wallet_not_verified"));
  assert.ok(result.reasons.includes("account_too_new"));
  assert.ok(result.reasons.includes("hyperactive_device"));
  assert.ok(result.reasons.includes("device_not_verified"));
});

test("missing risk and cluster signals fail closed", () => {
  const account = genuine(ADDRESS_A);
  delete account.riskScore;
  delete account.clusterId;
  const result = scoreAccount(account);
  assert.equal(result.eligible, false);
  assert.ok(result.reasons.includes("risk_score_missing"));
  assert.ok(result.reasons.includes("cluster_missing"));
});

test("drop waits for a minimum genuine population", () => {
  const result = allocateDrop(
    [genuine(ADDRESS_A)],
    1_000_000n,
    { ...DEFAULT_DROP_POLICY, minimumEligibleAccounts: 2 }
  );
  assert.equal(result.status, "minimum_not_reached");
  assert.equal(result.distributed, 0n);
});

test("wallet and device-cluster caps bound allocations", () => {
  const policy = {
    ...DEFAULT_DROP_POLICY,
    minimumEligibleAccounts: 3,
    accountCapPpm: 200_000,
    clusterCapBps: 2_500
  };
  const result = allocateDrop(
    [
      genuine(ADDRESS_A, { clusterId: "shared" }),
      genuine(ADDRESS_B, { clusterId: "shared" }),
      genuine(ADDRESS_C, { clusterId: "independent" })
    ],
    1_000_000n,
    policy
  );
  assert.equal(result.status, "ready");
  assert.ok(result.distributed <= 1_000_000n);
  for (const allocation of result.allocations) {
    assert.ok(allocation.amount <= 200_000n);
  }
  const shared = result.allocations
    .filter((item) => item.clusterId === "shared")
    .reduce((total, item) => total + item.amount, 0n);
  assert.ok(shared <= 250_000n);
});

test("default wallet cap is 0.00001 of the weekly pool", () => {
  const result = allocateDrop(
    [
      genuine(ADDRESS_A),
      genuine(ADDRESS_B),
      genuine(ADDRESS_C)
    ],
    1_000_000n,
    { ...DEFAULT_DROP_POLICY, minimumEligibleAccounts: 3 }
  );
  assert.equal(DEFAULT_DROP_POLICY.accountCapPpm, 10);
  assert.equal(result.status, "ready");
  assert.equal(result.distributed, 30n);
  assert.ok(result.allocations.every((item) => item.amount <= 10n));
});

test("generated Merkle proofs use the distributor leaf format", () => {
  const tree = buildDropTree(7, [
    { account: ADDRESS_A, amount: 100n },
    { account: ADDRESS_B, amount: 200n }
  ]);
  assert.match(tree.root, /^0x[0-9a-f]{64}$/i);
  assert.equal(tree.claims.length, 2);
  assert.ok(tree.claims.every((claim) => claim.proof.length === 1));
});
