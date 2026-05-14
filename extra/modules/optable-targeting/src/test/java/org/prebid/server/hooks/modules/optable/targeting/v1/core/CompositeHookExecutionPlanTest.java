package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeHookExecutionPlanTest {

    @Test
    public void hasRawAuctionRequestHookShouldReturnTrueWhenGlobalPlanHasHook() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnTrueWhenAccountPlanHasHook() {
        // given
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null);
        final Account account = givenAccount("accountId", accountPlan);

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnTrueWhenBothPlansHaveHook() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = givenAccount("accountId", accountPlan);

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnFalseWhenNeitherPlanHasHook() {
        // given
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isFalse();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnFalseWhenAccountIsNull() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);

        // when and then
        assertThat(target.hasRawAuctionRequestHook(null)).isFalse();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnFalseWhenAccountIdIsEmpty() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("").build();

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isFalse();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnGlobalFlagWhenAccountHasNoHooksConfig() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnSameResultOnRepeatedCallsForSameAccount() {
        // given
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null);
        final Account account = givenAccount("accountId", accountPlan);

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isTrue();
        assertThat(target.hasRawAuctionRequestHook(account)).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnTrueWhenGlobalPlanHasHook() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnTrueWhenAccountPlanHasHook() {
        // given
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null);
        final Account account = givenAccount("accountId", accountPlan);

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnTrueWhenBothPlansHaveHook() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = givenAccount("accountId", accountPlan);

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnFalseWhenNeitherPlanHasHook() {
        // given
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isFalse();
    }

    @Test
    public void hasBidderRequestHookShouldReturnFalseWhenAccountIsNull() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);

        // when and then
        assertThat(target.hasBidderRequestHook(null)).isFalse();
    }

    @Test
    public void hasBidderRequestHookShouldReturnFalseWhenAccountIdIsEmpty() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("").build();

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isFalse();
    }

    @Test
    public void hasBidderRequestHookShouldReturnGlobalFlagWhenAccountHasNoHooksConfig() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnSameResultOnRepeatedCallsForSameAccount() {
        // given
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null);
        final Account account = givenAccount("accountId", accountPlan);

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isTrue();
        assertThat(target.hasBidderRequestHook(account)).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnFalseWhenOnlyBidderRequestHookIsInGlobalPlan() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasRawAuctionRequestHook(account)).isFalse();
    }

    @Test
    public void hasBidderRequestHookShouldReturnFalseWhenOnlyRawAuctionRequestHookIsInGlobalPlan() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan);
        final Account account = Account.builder().id("accountId").build();

        // when and then
        assertThat(target.hasBidderRequestHook(account)).isFalse();
    }

    private ExecutionPlan givenExecutionPlan(String stage, String hookCode) {
        final HookId hookId = HookId.of("optable-targeting", hookCode);
        final ExecutionGroup group = ExecutionGroup.of(null, List.of(hookId));
        final StageExecutionPlan stagePlan = StageExecutionPlan.of(List.of(group));
        final EndpointExecutionPlan endpointPlan = EndpointExecutionPlan.of(Map.of(Stage.valueOf(stage), stagePlan));
        return ExecutionPlan.of(null, Map.of(Endpoint.openrtb2_auction, endpointPlan));
    }

    private Account givenAccount(String accountId, ExecutionPlan executionPlan) {
        return Account.builder()
                .id(accountId)
                .hooks(AccountHooksConfiguration.of(executionPlan, null, null))
                .build();
    }
}

