// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title Chengyu Points
/// @notice Non-transferable loyalty points with an auditable, idempotent ledger.
/// @dev Points are deliberately not ERC-20 tokens: there is no transfer, approval,
///      or market-facing interface. Only approved issuers can award or spend points.
contract ChengyuPoints {
    string public constant name = "Chengyu Points";
    string public constant symbol = "CYP";

    address public owner;
    address public pendingOwner;
    bool public paused;
    uint256 public totalIssued;
    uint256 public totalSpent;

    mapping(address account => uint256 balance) private _balances;
    mapping(address issuer => bool enabled) public issuers;
    mapping(bytes32 operationId => bool processed) public processedReferences;

    error Unauthorized();
    error ZeroAddress();
    error ZeroAmount();
    error EmptyReference();
    error DuplicateReference(bytes32 operationId);
    error InsufficientPoints(uint256 available, uint256 requested);
    error ContractPaused();

    event PointsAwarded(
        address indexed account,
        uint256 amount,
        bytes32 indexed operationId,
        address indexed issuer
    );
    event PointsSpent(
        address indexed account,
        uint256 amount,
        bytes32 indexed operationId,
        address indexed issuer
    );
    event IssuerUpdated(address indexed issuer, bool enabled);
    event PauseUpdated(bool paused);
    event OwnershipTransferStarted(
        address indexed previousOwner,
        address indexed pendingOwner
    );
    event OwnershipTransferred(
        address indexed previousOwner,
        address indexed newOwner
    );

    modifier onlyOwner() {
        if (msg.sender != owner) revert Unauthorized();
        _;
    }

    modifier onlyIssuer() {
        if (!issuers[msg.sender]) revert Unauthorized();
        _;
    }

    modifier whenNotPaused() {
        if (paused) revert ContractPaused();
        _;
    }

    constructor() {
        owner = msg.sender;
        issuers[msg.sender] = true;
        emit IssuerUpdated(msg.sender, true);
        emit OwnershipTransferred(address(0), msg.sender);
    }

    function balanceOf(address account) external view returns (uint256) {
        return _balances[account];
    }

    function award(
        address account,
        uint256 amount,
        bytes32 operationId
    ) external onlyIssuer whenNotPaused {
        _validateEntry(account, amount, operationId);
        processedReferences[operationId] = true;
        _balances[account] += amount;
        totalIssued += amount;
        emit PointsAwarded(account, amount, operationId, msg.sender);
    }

    function spend(
        address account,
        uint256 amount,
        bytes32 operationId
    ) external onlyIssuer whenNotPaused {
        _validateEntry(account, amount, operationId);
        uint256 available = _balances[account];
        if (available < amount) {
            revert InsufficientPoints(available, amount);
        }
        processedReferences[operationId] = true;
        unchecked {
            _balances[account] = available - amount;
        }
        totalSpent += amount;
        emit PointsSpent(account, amount, operationId, msg.sender);
    }

    function setIssuer(address issuer, bool enabled) external onlyOwner {
        if (issuer == address(0)) revert ZeroAddress();
        issuers[issuer] = enabled;
        emit IssuerUpdated(issuer, enabled);
    }

    function setPaused(bool value) external onlyOwner {
        paused = value;
        emit PauseUpdated(value);
    }

    function transferOwnership(address newOwner) external onlyOwner {
        if (newOwner == address(0)) revert ZeroAddress();
        pendingOwner = newOwner;
        emit OwnershipTransferStarted(owner, newOwner);
    }

    function acceptOwnership() external {
        if (msg.sender != pendingOwner) revert Unauthorized();
        address previousOwner = owner;
        owner = msg.sender;
        pendingOwner = address(0);
        emit OwnershipTransferred(previousOwner, msg.sender);
    }

    function _validateEntry(
        address account,
        uint256 amount,
        bytes32 operationId
    ) private view {
        if (account == address(0)) revert ZeroAddress();
        if (amount == 0) revert ZeroAmount();
        if (operationId == bytes32(0)) revert EmptyReference();
        if (processedReferences[operationId]) {
            revert DuplicateReference(operationId);
        }
    }
}
