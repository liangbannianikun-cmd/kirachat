import { getAddress, isAddress } from "ethers";
import { StandardMerkleTree } from "@openzeppelin/merkle-tree";

export const DEFAULT_DROP_POLICY = Object.freeze({
  minimumEligibleAccounts: 100,
  minimumAccountAgeDays: 14,
  minimumWalletBoundDays: 7,
  minimumActiveDays: 3,
  maximumRiskScore: 49,
  maximumAccountsPerDevice: 2,
  // 10 parts per million = 0.00001 of the weekly pool = 0.001%.
  accountCapPpm: 10,
  clusterCapBps: 300
});

export function scoreAccount(input, policy = DEFAULT_DROP_POLICY) {
  const reasons = [];
  if (!isAddress(input.account || "")) reasons.push("invalid_wallet");
  if (!input.walletSignatureVerified) reasons.push("wallet_not_verified");
  if (input.banned) reasons.push("banned");
  if (number(input.accountAgeDays) < policy.minimumAccountAgeDays) {
    reasons.push("account_too_new");
  }
  if (number(input.walletBoundDays) < policy.minimumWalletBoundDays) {
    reasons.push("wallet_binding_too_new");
  }
  if (number(input.activeDays) < policy.minimumActiveDays) {
    reasons.push("not_enough_active_days");
  }
  if (!isNonNegativeNumber(input.riskScore)) {
    reasons.push("risk_score_missing");
  } else if (number(input.riskScore) > policy.maximumRiskScore) {
    reasons.push("risk_score_too_high");
  }
  if (number(input.deviceAccountCount, 1) > policy.maximumAccountsPerDevice) {
    reasons.push("too_many_accounts_on_device");
  }
  if (!String(input.clusterId || "").trim()) {
    reasons.push("cluster_missing");
  }
  if (input.recentDeviceActivity !== "normal") {
    reasons.push("hyperactive_device");
  }

  const integrityBps = integrityMultiplier(input);
  if (integrityBps === 0) reasons.push("device_not_verified");
  if (reasons.length > 0) {
    return {
      account: input.account || "",
      eligible: false,
      score: 0,
      reasons
    };
  }

  const activity =
    Math.min(number(input.activeDays), 5) * 10 +
    Math.min(number(input.meaningfulSessions), 6) * 8 +
    Math.min(number(input.acceptedContributions), 2) * 60 +
    Math.min(number(input.helpfulReports), 2) * 20 +
    Math.min(number(input.streakWeeks), 4) * 5;
  const riskBps = riskMultiplier(number(input.riskScore));
  const score = Math.floor((activity * integrityBps * riskBps) / 100_000_000);

  return {
    account: getAddress(input.account),
    clusterId: cleanCluster(input.clusterId, input.account),
    eligible: score > 0,
    score,
    reasons: score > 0 ? [] : ["zero_score"]
  };
}

export function allocateDrop(
  accounts,
  pool,
  policy = DEFAULT_DROP_POLICY
) {
  const poolAmount = BigInt(pool);
  if (poolAmount <= 0n) throw new Error("pool must be positive");
  const scored = accounts.map((account) => ({
    input: account,
    result: scoreAccount(account, policy)
  }));
  const eligible = scored.filter(({ result }) => result.eligible);
  if (eligible.length < policy.minimumEligibleAccounts) {
    return {
      allocations: [],
      distributed: 0n,
      undistributed: poolAmount,
      eligibleCount: eligible.length,
      rejected: rejectedSummary(scored),
      status: "minimum_not_reached"
    };
  }

  const weighted = eligible.map(({ result }) => ({
    ...result,
    weight: integerSqrt(BigInt(result.score) * 1_000_000n)
  }));
  const totalWeight = weighted.reduce(
    (total, item) => total + item.weight,
    0n
  );
  const accountCap =
    (poolAmount * BigInt(policy.accountCapPpm)) / 1_000_000n;
  const clusterCap = (poolAmount * BigInt(policy.clusterCapBps)) / 10_000n;

  const provisional = weighted.map((item) => {
    const proportional = (poolAmount * item.weight) / totalWeight;
    return {
      ...item,
      amount: proportional > accountCap ? accountCap : proportional
    };
  });

  const clusters = new Map();
  for (const item of provisional) {
    const members = clusters.get(item.clusterId) || [];
    members.push(item);
    clusters.set(item.clusterId, members);
  }

  const allocations = [];
  for (const members of clusters.values()) {
    const clusterTotal = members.reduce(
      (total, item) => total + item.amount,
      0n
    );
    for (const item of members) {
      const amount = clusterTotal > clusterCap
        ? (item.amount * clusterCap) / clusterTotal
        : item.amount;
      if (amount > 0n) {
        allocations.push({
          account: item.account,
          amount,
          score: item.score,
          clusterId: item.clusterId
        });
      }
    }
  }

  allocations.sort((left, right) =>
    left.account.toLowerCase().localeCompare(right.account.toLowerCase())
  );
  const distributed = allocations.reduce(
    (total, item) => total + item.amount,
    0n
  );
  if (distributed > poolAmount) throw new Error("allocation exceeds pool");
  return {
    allocations,
    distributed,
    undistributed: poolAmount - distributed,
    eligibleCount: eligible.length,
    rejected: rejectedSummary(scored),
    status: "ready"
  };
}

export function buildDropTree(epoch, allocations) {
  if (!Number.isSafeInteger(epoch) || epoch < 0) {
    throw new Error("epoch must be a non-negative integer");
  }
  if (allocations.length === 0) throw new Error("no allocations");
  const values = allocations.map((item) => [
    epoch.toString(),
    getAddress(item.account),
    BigInt(item.amount).toString()
  ]);
  const tree = StandardMerkleTree.of(
    values,
    ["uint256", "address", "uint256"]
  );
  const claims = [];
  for (const [index, value] of tree.entries()) {
    claims.push({
      epoch,
      account: value[1],
      amount: value[2],
      proof: tree.getProof(index)
    });
  }
  claims.sort((left, right) =>
    left.account.toLowerCase().localeCompare(right.account.toLowerCase())
  );
  return { root: tree.root, claims };
}

function integrityMultiplier(input) {
  if (input.deviceIntegrity === "strong") return 10_000;
  if (input.deviceIntegrity === "device") return 9_000;
  if (input.manualIdentityVerified) return 7_000;
  return 0;
}

function riskMultiplier(riskScore) {
  if (riskScore <= 10) return 10_000;
  if (riskScore <= 25) return 7_500;
  if (riskScore <= 40) return 4_000;
  return 1_500;
}

function rejectedSummary(scored) {
  const counts = {};
  for (const { result } of scored) {
    if (result.eligible) continue;
    for (const reason of result.reasons) {
      counts[reason] = (counts[reason] || 0) + 1;
    }
  }
  return counts;
}

function integerSqrt(value) {
  if (value < 0n) throw new Error("square root of negative value");
  if (value < 2n) return value;
  let left = 1n;
  let right = value;
  while (left <= right) {
    const middle = (left + right) >> 1n;
    const square = middle * middle;
    if (square === value) return middle;
    if (square < value) left = middle + 1n;
    else right = middle - 1n;
  }
  return right;
}

function number(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

function isNonNegativeNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0;
}

function cleanCluster(clusterId, account) {
  const value = String(clusterId || "").trim();
  return value || `wallet:${String(account).toLowerCase()}`;
}
