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
const tokenArtifact = await artifact("CCCoin.sol", "CCCoin");
const distributorArtifact = await artifact(
  "CCEmissionDistributor.sol",
  "CCEmissionDistributor"
);

const rpcUrl = process.env.RPC_URL || "https://sepolia.base.org";
const expectedChainId = BigInt(process.env.EXPECTED_CHAIN_ID || "84532");
const privateKey = required("DEPLOYER_PRIVATE_KEY");
const tokenName = process.env.CC_NAME?.trim() || "CC Coin";
const tokenSymbol = required("CC_SYMBOL");
const startTime = parseStartTime(required("EMISSION_START_TIME"));
const finalAdmin = process.env.FINAL_ADMIN_ADDRESS
  ? getAddress(process.env.FINAL_ADMIN_ADDRESS)
  : null;
const rootPublisher = process.env.ROOT_PUBLISHER_ADDRESS
  ? getAddress(process.env.ROOT_PUBLISHER_ADDRESS)
  : finalAdmin;
if (!rootPublisher) {
  throw new Error(
    "ROOT_PUBLISHER_ADDRESS or FINAL_ADMIN_ADDRESS is required"
  );
}

const provider = new JsonRpcProvider(rpcUrl);
const network = await provider.getNetwork();
if (network.chainId !== expectedChainId) {
  throw new Error(
    `Refusing deployment: expected chain ${expectedChainId}, got ${network.chainId}`
  );
}
const wallet = new Wallet(privateKey, provider);
const signer = new NonceManager(wallet);

const tokenFactory = new ContractFactory(
  tokenArtifact.abi,
  tokenArtifact.bytecode,
  signer
);
const token = await tokenFactory.deploy(tokenName, tokenSymbol, wallet.address);
await token.waitForDeployment();
const tokenAddress = await token.getAddress();
console.log(`CC token: ${tokenAddress}`);
console.log(`Token transaction: ${token.deploymentTransaction()?.hash}`);

const distributorFactory = new ContractFactory(
  distributorArtifact.abi,
  distributorArtifact.bytecode,
  signer
);
const distributor = await distributorFactory.deploy(
  tokenAddress,
  startTime,
  wallet.address,
  rootPublisher
);
await distributor.waitForDeployment();
const distributorAddress = await distributor.getAddress();
console.log(`Emission distributor: ${distributorAddress}`);
console.log(
  `Distributor transaction: ${distributor.deploymentTransaction()?.hash}`
);

const minterRole = await token.MINTER_ROLE();
await confirmed(token.grantRole(minterRole, distributorAddress));
console.log("Emission distributor received MINTER_ROLE");

if (finalAdmin && finalAdmin !== wallet.address) {
  const defaultAdminRole = await token.DEFAULT_ADMIN_ROLE();
  await confirmed(token.grantRole(defaultAdminRole, finalAdmin));
  await confirmed(token.grantRole(minterRole, finalAdmin));
  await confirmed(
    distributor.grantRole(
      await distributor.DEFAULT_ADMIN_ROLE(),
      finalAdmin
    )
  );
  console.log(`Final admin enabled: ${finalAdmin}`);

  if (process.env.RENOUNCE_DEPLOYER === "true") {
    await confirmed(token.renounceRole(minterRole, wallet.address));
    await confirmed(token.renounceRole(defaultAdminRole, wallet.address));
    await confirmed(
      distributor.renounceRole(
        await distributor.DEFAULT_ADMIN_ROLE(),
        wallet.address
      )
    );
    console.log("Deployer roles renounced");
  } else {
    console.log(
      "Deployer roles retained. Set RENOUNCE_DEPLOYER=true only after " +
      "verifying the final multisig."
    );
  }
}

console.log(`Name: ${tokenName}`);
console.log(`Symbol: ${tokenSymbol}`);
console.log("Maximum supply: 1145141919810");
console.log(`Emission start: ${startTime}`);
console.log(`Root publisher: ${rootPublisher}`);

async function artifact(source, contract) {
  const file = path.join(
    root,
    "artifacts",
    "contracts",
    source,
    `${contract}.json`
  );
  return JSON.parse(await readFile(file, "utf8"));
}

async function confirmed(transactionPromise) {
  const transaction = await transactionPromise;
  const receipt = await transaction.wait(1);
  if (!receipt || receipt.status !== 1) {
    throw new Error(`Transaction failed: ${transaction.hash}`);
  }
}

function parseStartTime(value) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error("EMISSION_START_TIME must be a Unix timestamp in seconds");
  }
  return parsed;
}

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}
