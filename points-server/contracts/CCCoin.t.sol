// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {CCCoin} from "./CCCoin.sol";
import {CCEmissionDistributor} from "./CCEmissionDistributor.sol";

interface Vm {
    function warp(uint256 newTimestamp) external;
}

contract UnauthorizedMinter {
    function tryMint(
        CCCoin token,
        address account,
        uint256 amount
    ) external returns (bool) {
        (bool ok,) = address(token).call(
            abi.encodeCall(token.mint, (account, amount))
        );
        return ok;
    }
}

contract CCCoinTest {
    CCCoin private token;
    address private constant RECEIVER = address(0xCC);

    function setUp() public {
        token = new CCCoin("CC Coin", "CC", address(this));
    }

    function test_MetadataAndCap() public view {
        require(
            token.MAX_SUPPLY() == 1_145_141_919_810 ether,
            "cap mismatch"
        );
        require(token.totalSupply() == 0, "supply should start at zero");
        require(
            token.hasRole(token.MINTER_ROLE(), address(this)),
            "admin should mint"
        );
    }

    function test_MintTransferAndBurn() public {
        token.mint(address(this), 100 ether);
        require(token.transfer(RECEIVER, 25 ether), "transfer failed");
        require(token.balanceOf(RECEIVER) == 25 ether, "receiver mismatch");
        token.burn(10 ether);
        require(token.balanceOf(address(this)) == 65 ether, "burn mismatch");
        require(token.totalSupply() == 90 ether, "supply mismatch");
    }

    function test_UnauthorizedMintReverts() public {
        UnauthorizedMinter minter = new UnauthorizedMinter();
        require(
            !minter.tryMint(token, RECEIVER, 1 ether),
            "unauthorized mint succeeded"
        );
    }

    function test_CapCannotBeExceeded() public {
        token.mint(address(this), token.MAX_SUPPLY());
        (bool ok,) = address(token).call(
            abi.encodeCall(token.mint, (address(this), 1))
        );
        require(!ok, "mint exceeded cap");
    }
}

contract CCEmissionDistributorTest {
    Vm private constant vm =
        Vm(address(uint160(uint256(keccak256("hevm cheat code")))));
    CCCoin private token;
    CCEmissionDistributor private distributor;

    function setUp() public {
        vm.warp(30 days);
        token = new CCCoin("CC Coin", "CC", address(this));
        distributor = new CCEmissionDistributor(
            address(token),
            1,
            address(this),
            address(this)
        );
        token.grantRole(token.MINTER_ROLE(), address(distributor));
    }

    function test_EmissionCurve() public view {
        require(
            distributor.emissionForYear(0) == 959_905 ether,
            "year 1"
        );
        require(
            distributor.emissionForYear(1) == 959_905 ether,
            "year 2"
        );
        require(
            distributor.emissionForYear(2) == 28_629 ether,
            "year 3"
        );
        require(
            distributor.emissionForYear(5) == 28_628 ether,
            "year 6"
        );
        require(
            distributor.emissionForYear(6) == 8_964 ether,
            "year 7"
        );
        require(
            distributor.emissionForYear(10) == 4_482 ether,
            "first halving"
        );
        require(
            distributor.emissionForYear(14) == 2_241 ether,
            "second halving"
        );
    }

    function test_PublishAndClaimSingleLeaf() public {
        uint256 epoch = 0;
        uint256 amount = 12 ether;
        bytes32 leaf = keccak256(
            bytes.concat(
                keccak256(abi.encode(epoch, address(this), amount))
            )
        );
        distributor.publishEpoch(epoch, leaf, amount);

        bytes32[] memory proof = new bytes32[](0);
        distributor.claim(epoch, address(this), amount, proof);
        require(token.balanceOf(address(this)) == amount, "claim failed");

        (bool ok,) = address(distributor).call(
            abi.encodeCall(
                distributor.claim,
                (epoch, address(this), amount, proof)
            )
        );
        require(!ok, "duplicate claim succeeded");
    }

    function test_AnnualBudgetCannotBeExceeded() public {
        uint256 tooMuch = distributor.emissionForYear(0) + 1;
        (bool ok,) = address(distributor).call(
            abi.encodeCall(
                distributor.publishEpoch,
                (0, keccak256("root"), tooMuch)
            )
        );
        require(!ok, "annual budget exceeded");
    }

    function test_ClaimsCannotExceedDeclaredEpochTotal() public {
        uint256 epoch = 0;
        uint256 leafAmount = 12 ether;
        bytes32 leaf = keccak256(
            bytes.concat(
                keccak256(
                    abi.encode(epoch, address(this), leafAmount)
                )
            )
        );
        distributor.publishEpoch(epoch, leaf, 10 ether);
        bytes32[] memory proof = new bytes32[](0);
        (bool ok,) = address(distributor).call(
            abi.encodeCall(
                distributor.claim,
                (epoch, address(this), leafAmount, proof)
            )
        );
        require(!ok, "claim exceeded declared total");
        require(token.totalSupply() == 0, "failed claim minted tokens");
    }

    function test_ExpiredEpochCannotBePublished() public {
        vm.warp(50 days);
        (bool ok,) = address(distributor).call(
            abi.encodeCall(
                distributor.publishEpoch,
                (0, keccak256("expired-root"), 1 ether)
            )
        );
        require(!ok, "expired epoch published");
    }
}
