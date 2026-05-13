package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.model.Endpoint;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeHookExecutionPlanTest {

    @Test
    public void hasRawAuctionRequestHookShouldReturnTrueWhenAccountPlanHasHook() {
        // given
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null, accountPlan);

        // when and then
        assertThat(target.hasRawAuctionRequestHook()).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnTrueWhenGlobalPlanHasHook() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "raw_auction_request", "optable-targeting-raw-auction-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan, null);

        // when and then
        assertThat(target.hasRawAuctionRequestHook()).isTrue();
    }

    @Test
    public void hasRawAuctionRequestHookShouldReturnFalseWhenNeitherPlanHasHook() {
        // given
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null, null);

        // when and then
        assertThat(target.hasRawAuctionRequestHook()).isFalse();
    }

    @Test
    public void hasBidderRequestHookShouldReturnTrueWhenAccountPlanHasHook() {
        // given
        final ExecutionPlan accountPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null, accountPlan);

        // when and then
        assertThat(target.hasBidderRequestHook()).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnTrueWhenGlobalPlanHasHook() {
        // given
        final ExecutionPlan globalPlan = givenExecutionPlan(
                "bidder_request", "optable-targeting-bidder-request-hook");
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(globalPlan, null);

        // when and then
        assertThat(target.hasBidderRequestHook()).isTrue();
    }

    @Test
    public void hasBidderRequestHookShouldReturnFalseWhenNeitherPlanHasHook() {
        // given
        final CompositeHookExecutionPlan target = CompositeHookExecutionPlan.of(null, null);

        // when and then
        assertThat(target.hasBidderRequestHook()).isFalse();
    }

    private ExecutionPlan givenExecutionPlan(String stage, String hookCode) {
        final HookId hookId = HookId.of("optable-targeting", hookCode);
        final ExecutionGroup group = ExecutionGroup.of(null, List.of(hookId));
        final StageExecutionPlan stagePlan = StageExecutionPlan.of(List.of(group));
        final EndpointExecutionPlan endpointPlan = EndpointExecutionPlan.of(Map.of(Stage.valueOf(stage), stagePlan));
        return ExecutionPlan.of(null, Map.of(Endpoint.openrtb2_auction, endpointPlan));
    }
}
