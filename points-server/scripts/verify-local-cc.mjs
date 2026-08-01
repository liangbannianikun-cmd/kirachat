import {
  AbiCoder,
  Contract,
  JsonRpcProvider,
  NonceManager,
  Wallet,
  concat,
  formatEther,
  keccak256,
  parseEther
} from "ethers";

const provider = new JsonRpcProvider(
  process.env.RPC_URL || "http://127.0.0.1:8545"
);
const network = await provider.getNetwork();
if (network.chainId !== 31_337n) {
  throw new Error("This verification script only runs on chain 31337");
}

const publisherWallet = new Wallet(
  required("TEST_PUBLISHER_PRIVATE_KEY"),
  provider
);
const publisher = new NonceManager(publisherWallet);
const token = new Contract(
  required("CC_TOKEN_ADDRESS"),
  [
    "function balanceOf(address) view returns(uint256)",
    "function totalSupply() view returns(uint256)",
    "function transfer(address,uint256) returns(bool)",
    "function burn(uint256)"
  ],
  publisher
);
const distributor = new Contract(
  required("CC_DISTRIBUTOR_ADDRESS"),
  [
    "function publishEpoch(uint256,bytes32,uint256)",
    "function claim(uint256,address,uint256,bytes32[])",
    "function claimed(uint256,address) view returns(bool)"
  ],
  publisher
);

const epoch = BigInt(process.env.TEST_EPOCH || "0");
const amount = parseEther(process.env.TEST_DROP_AMOUNT || "12");
const receiver = process.env.TEST_RECEIVER_ADDRESS || Wallet.createRandom().address;
const encoded = AbiCoder.defaultAbiCoder().encode(
  ["uint256", "address", "uint256"],
  [epoch, publisherWallet.address, amount]
);
const leaf = keccak256(concat([keccak256(encoded)]));

await confirmed(distributor.publishEpoch(epoch, leaf, amount));
await confirmed(distributor.claim(epoch, publisherWallet.address, amount, []));
await confirmed(token.transfer(receiver, parseEther("1")));
await confirmed(token.burn(parseEther("2")));

console.log(JSON.stringify({
  claimed: await distributor.claimed(epoch, publisherWallet.address),
  publisherBalance: formatEther(
    await token.balanceOf(publisherWallet.address)
  ),
  receiverBalance: formatEther(await token.balanceOf(receiver)),
  totalSupply: formatEther(await token.totalSupply())
}, null, 2));

async function confirmed(transactionPromise) {
  const transaction = await transactionPromise;
  const receipt = await transaction.wait(1);
  if (!receipt || receipt.status !== 1) {
    throw new Error(`Transaction failed: ${transaction.hash}`);
  }
}

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}
