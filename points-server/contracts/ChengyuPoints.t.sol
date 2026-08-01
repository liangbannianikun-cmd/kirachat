// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {ChengyuPoints} from "./ChengyuPoints.sol";

contract UntrustedCaller {
    function tryAward(
        ChengyuPoints points,
        address account,
        bytes32 operationId
    ) external returns (bool) {
        (bool ok,) = address(points).call(
            abi.encodeCall(points.award, (account, 1, operationId))
        );
        return ok;
    }
}

contract ChengyuPointsTest {
    ChengyuPoints private points;
    address private constant ACCOUNT = address(0x1234);

    function setUp() public {
        points = new ChengyuPoints();
    }

    function test_InitialState() public view {
        require(points.owner() == address(this), "owner mismatch");
        require(points.issuers(address(this)), "owner should issue");
        require(points.balanceOf(ACCOUNT) == 0, "balance should be zero");
    }

    function test_AwardAndSpend() public {
        bytes32 awardReference = keccak256("award-1");
        bytes32 spendReference = keccak256("spend-1");

        points.award(ACCOUNT, 25, awardReference);
        require(points.balanceOf(ACCOUNT) == 25, "award failed");
        require(points.totalIssued() == 25, "issued total mismatch");
        require(
            points.processedReferences(awardReference),
            "reference not recorded"
        );

        points.spend(ACCOUNT, 7, spendReference);
        require(points.balanceOf(ACCOUNT) == 18, "spend failed");
        require(points.totalSpent() == 7, "spent total mismatch");
    }

    function test_DuplicateReferenceReverts() public {
        bytes32 operationId = keccak256("same-reference");
        points.award(ACCOUNT, 5, operationId);
        (bool ok,) = address(points).call(
            abi.encodeCall(points.award, (ACCOUNT, 5, operationId))
        );
        require(!ok, "duplicate reference should revert");
        require(points.balanceOf(ACCOUNT) == 5, "balance changed twice");
    }

    function test_InsufficientBalanceReverts() public {
        points.award(ACCOUNT, 2, keccak256("small-award"));
        (bool ok,) = address(points).call(
            abi.encodeCall(
                points.spend,
                (ACCOUNT, 3, keccak256("large-spend"))
            )
        );
        require(!ok, "overspend should revert");
        require(points.balanceOf(ACCOUNT) == 2, "failed spend changed balance");
    }

    function test_UntrustedCallerCannotIssue() public {
        UntrustedCaller caller = new UntrustedCaller();
        bool ok = caller.tryAward(
            points,
            ACCOUNT,
            keccak256("unauthorized")
        );
        require(!ok, "untrusted caller issued points");
    }

    function test_PauseStopsAwards() public {
        points.setPaused(true);
        (bool ok,) = address(points).call(
            abi.encodeCall(
                points.award,
                (ACCOUNT, 1, keccak256("paused-award"))
            )
        );
        require(!ok, "paused award should revert");
    }
}
