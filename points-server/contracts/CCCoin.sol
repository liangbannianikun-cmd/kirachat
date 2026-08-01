// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {
    ERC20Burnable
} from "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import {
    ERC20Capped
} from "@openzeppelin/contracts/token/ERC20/extensions/ERC20Capped.sol";

/// @title CC Coin
/// @notice Freely transferable, burnable ERC-20 with controlled minting.
/// @dev The name and symbol are constructor arguments and become fixed at
///      deployment. There is intentionally no pause or blocklist mechanism.
contract CCCoin is ERC20, ERC20Burnable, ERC20Capped, AccessControl {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    uint256 public constant MAX_SUPPLY = 1_145_141_919_810 ether;

    error ZeroAdmin();

    constructor(
        string memory name_,
        string memory symbol_,
        address initialAdmin
    ) ERC20(name_, symbol_) ERC20Capped(MAX_SUPPLY) {
        if (initialAdmin == address(0)) revert ZeroAdmin();
        _grantRole(DEFAULT_ADMIN_ROLE, initialAdmin);
        _grantRole(MINTER_ROLE, initialAdmin);
    }

    /// @notice Mints within the immutable maximum supply.
    function mint(
        address account,
        uint256 amount
    ) external onlyRole(MINTER_ROLE) {
        _mint(account, amount);
    }

    function _update(
        address from,
        address to,
        uint256 value
    ) internal override(ERC20, ERC20Capped) {
        super._update(from, to, value);
    }
}
