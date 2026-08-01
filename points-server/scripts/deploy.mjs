import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import {
  ContractFactory,
  JsonRpcProvider,
  NonceManager,
  Wallet,
  getAddress
} from "ethers";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");
const artifactPath = path.join(
  root,
  "artifacts",
  "contracts",
  "ChengyuPoints.sol",
  "ChengyuPoints.json"
);

const rpcUrl = process.env.RPC_URL || "https://sepolia.base.org";
const expectedChainId = BigInt(process.env.EXPECTED_CHAIN_ID || "84532");
const deployerKey = required("DEPLOYER_PRIVATE_KEY");
const requestedIssuer = process.env.ISSUER_ADDRESS?.trim();

const artifact = JSON.parse(await readFile(artifactPath, "utf8"));
const provider = new JsonRpcProvider(rpcUrl);
const network = await provider.getNetwork();
if (network.chainId !== expectedChainId) {
  throw new Error(
    `Refusing deployment: expected chain ${expectedChainId}, got ${network.chainId}`
  );
}

const deployer = new Wallet(deployerKey, provider);
const deployerSigner = new NonceManager(deployer);
const factory = new ContractFactory(
  artifact.abi,
  artifact.bytecode,
  deployerSigner
);
const contract = await factory.deploy();
await contract.waitForDeployment();

const address = await contract.getAddress();
const deployment = contract.deploymentTransaction();
console.log(`Contract: ${address}`);
console.log(`Deployment transaction: ${deployment?.hash || "unknown"}`);

if (requestedIssuer) {
  const issuer = getAddress(requestedIssuer);
  if (issuer !== deployer.address) {
    const transaction = await contract.setIssuer(issuer, true);
    await transaction.wait(1);
    console.log(`Issuer enabled: ${issuer}`);
    console.log(`Issuer transaction: ${transaction.hash}`);
  }
}

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}
