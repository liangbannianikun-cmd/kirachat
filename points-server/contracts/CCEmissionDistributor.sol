// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {
    MerkleProof
} from "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

interface ICCMintable {
    function mint(address account, uint256 amount) external;
}

/// @title CC Emission Distributor
/// @notice Immutable annual drop caps with weekly Merkle claim epochs.
/// @dev Anti-bot scoring happens offchain. This contract independently ensures
///      that published entitlements cannot exceed the user's emission curve.
contract CCEmissionDistributor is AccessControl {
    bytes32 public constant ROOT_PUBLISHER_ROLE =
        keccak256("ROOT_PUBLISHER_ROLE");
    uint256 public constant WEEK = 7 days;
    uint256 public constant YEAR = 365 days;
    uint256 public constant MAX_PUBLISH_DELAY = 28 days;

    ICCMintable public immutable token;
    uint256 public immutable startTime;

    struct Epoch {
        bytes32 merkleRoot;
        uint256 totalAllocation;
        uint256 claimedAmount;
        uint256 emissionYear;
    }

    mapping(uint256 epoch => Epoch details) public epochs;
    mapping(uint256 emissionYear => uint256 allocated) public allocatedByYear;
    mapping(uint256 epoch => mapping(address account => bool claimed))
        public claimed;

    error ZeroAddress();
    error InvalidStartTime();
    error EpochNotEnded(uint256 epochEnd);
    error EpochPublishExpired(uint256 deadline);
    error EpochAlreadyPublished(uint256 epoch);
    error EmptyMerkleRoot();
    error ZeroAllocation();
    error AnnualBudgetExceeded(
        uint256 emissionYear,
        uint256 budget,
        uint256 requested
    );
    error EpochNotPublished(uint256 epoch);
    error AlreadyClaimed(uint256 epoch, address account);
    error InvalidProof();
    error EpochAllocationExceeded(uint256 declared, uint256 requested);

    event EpochPublished(
        uint256 indexed epoch,
        uint256 indexed emissionYear,
        bytes32 merkleRoot,
        uint256 totalAllocation
    );
    event DropClaimed(
        uint256 indexed epoch,
        address indexed account,
        uint256 amount
    );

    constructor(
        address token_,
        uint256 startTime_,
        address initialAdmin,
        address rootPublisher
    ) {
        if (
            token_ == address(0) ||
            initialAdmin == address(0) ||
            rootPublisher == address(0)
        ) revert ZeroAddress();
        if (startTime_ == 0) revert InvalidStartTime();
        token = ICCMintable(token_);
        startTime = startTime_;
        _grantRole(DEFAULT_ADMIN_ROLE, initialAdmin);
        _grantRole(ROOT_PUBLISHER_ROLE, rootPublisher);
    }

    /// @notice Publishes one immutable weekly claim root after that week ends.
    function publishEpoch(
        uint256 epoch,
        bytes32 merkleRoot,
        uint256 totalAllocation
    ) external onlyRole(ROOT_PUBLISHER_ROLE) {
        uint256 epochEnd = startTime + ((epoch + 1) * WEEK);
        if (block.timestamp < epochEnd) revert EpochNotEnded(epochEnd);
        uint256 deadline = epochEnd + MAX_PUBLISH_DELAY;
        if (block.timestamp > deadline) revert EpochPublishExpired(deadline);
        if (epochs[epoch].merkleRoot != bytes32(0)) {
            revert EpochAlreadyPublished(epoch);
        }
        if (merkleRoot == bytes32(0)) revert EmptyMerkleRoot();
        if (totalAllocation == 0) revert ZeroAllocation();

        uint256 emissionYear = (epochEnd - startTime - 1) / YEAR;
        uint256 budget = emissionForYear(emissionYear);
        uint256 requested = allocatedByYear[emissionYear] + totalAllocation;
        if (requested > budget) {
            revert AnnualBudgetExceeded(emissionYear, budget, requested);
        }

        epochs[epoch] = Epoch({
            merkleRoot: merkleRoot,
            totalAllocation: totalAllocation,
            claimedAmount: 0,
            emissionYear: emissionYear
        });
        allocatedByYear[emissionYear] = requested;
        emit EpochPublished(
            epoch,
            emissionYear,
            merkleRoot,
            totalAllocation
        );
    }

    /// @notice Claims an allocation for an account. Anyone may relay the proof;
    ///         the tokens always go to the account encoded in the leaf.
    function claim(
        uint256 epoch,
        address account,
        uint256 amount,
        bytes32[] calldata merkleProof
    ) external {
        Epoch storage details = epochs[epoch];
        if (details.merkleRoot == bytes32(0)) {
            revert EpochNotPublished(epoch);
        }
        if (claimed[epoch][account]) revert AlreadyClaimed(epoch, account);

        bytes32 leaf = keccak256(
            bytes.concat(keccak256(abi.encode(epoch, account, amount)))
        );
        if (
            !MerkleProof.verifyCalldata(
                merkleProof,
                details.merkleRoot,
                leaf
            )
        ) revert InvalidProof();

        uint256 requested = details.claimedAmount + amount;
        if (requested > details.totalAllocation) {
            revert EpochAllocationExceeded(
                details.totalAllocation,
                requested
            );
        }
        claimed[epoch][account] = true;
        details.claimedAmount = requested;
        token.mint(account, amount);
        emit DropClaimed(epoch, account, amount);
    }

    /// @notice Whole-token budget for a zero-based emission year.
    function emissionForYear(
        uint256 emissionYear
    ) public pure returns (uint256) {
        // First two years total: 1,919,810.
        if (emissionYear < 2) return 959_905 ether;

        // Following four years total: 114,514.
        if (emissionYear == 2 || emissionYear == 3) return 28_629 ether;
        if (emissionYear == 4 || emissionYear == 5) return 28_628 ether;

        // Years 7-10: 8,964 per year. Every four years after that halves.
        uint256 halving = (emissionYear - 6) / 4;
        return uint256(8_964 ether) >> halving;
    }
}
