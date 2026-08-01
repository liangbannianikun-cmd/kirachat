import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import {
  DEFAULT_DROP_POLICY,
  allocateDrop,
  buildDropTree
} from "../src/drop/scoring.mjs";

const inputPath = process.argv[2];
const outputPath = process.argv[3];
if (!inputPath || !outputPath) {
  throw new Error(
    "Usage: node scripts/build-drop.mjs <input.json> <output.json>"
  );
}

const input = JSON.parse(await readFile(path.resolve(inputPath), "utf8"));
const epoch = Number(input.epoch);
const policy = {
  ...DEFAULT_DROP_POLICY,
  ...(input.policy || {})
};
const result = allocateDrop(input.accounts || [], input.pool, policy);
if (result.status !== "ready") {
  throw new Error(
    `Drop not built: ${result.eligibleCount} eligible accounts, ` +
    `${policy.minimumEligibleAccounts} required`
  );
}
const tree = buildDropTree(epoch, result.allocations);
const output = {
  version: 1,
  epoch,
  merkleRoot: tree.root,
  pool: String(input.pool),
  totalAllocation: result.distributed.toString(),
  undistributed: result.undistributed.toString(),
  eligibleCount: result.eligibleCount,
  rejected: result.rejected,
  policy,
  claims: tree.claims
};
await writeFile(
  path.resolve(outputPath),
  `${JSON.stringify(output, null, 2)}\n`,
  "utf8"
);
console.log(`Merkle root: ${output.merkleRoot}`);
console.log(`Eligible accounts: ${output.eligibleCount}`);
console.log(`Allocated: ${output.totalAllocation}`);
console.log(`Unallocated: ${output.undistributed}`);
