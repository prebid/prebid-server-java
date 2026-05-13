package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import lombok.AllArgsConstructor;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.model.Endpoint;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor(staticName = "of")
public class CompositeHookExecutionPlan {

    private static final String ENDPOINT_AUCTION = "openrtb2_auction";
    private static final String STAGE_RAW_AUCTION_REQUEST = "raw_auction_request";
    private static final String STAGE_BIDDER_REQUEST = "bidder_request";
    private static final String HOOK_CODE_OPTABLE_RAW_AUCTION = "optable-targeting-raw-auction-request-hook";
    private static final String HOOK_CODE_OPTABLE_BIDDER_REQUEST = "optable-targeting-bidder-request-hook";

    private ExecutionPlan globalExecutionPlan;

    private ExecutionPlan accountExecutionPlan;

    public boolean hasRawAuctionRequestHook() {
        return hasHook(accountExecutionPlan, STAGE_RAW_AUCTION_REQUEST, HOOK_CODE_OPTABLE_RAW_AUCTION)
                || hasHook(globalExecutionPlan, STAGE_RAW_AUCTION_REQUEST, HOOK_CODE_OPTABLE_RAW_AUCTION);
    }

    public boolean hasBidderRequestHook() {
        return hasHook(accountExecutionPlan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST)
                || hasHook(globalExecutionPlan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST);
    }

    private static boolean hasHook(ExecutionPlan executionPlan, String stage, String hookCode) {
        return Optional.ofNullable(executionPlan)
                .map(ExecutionPlan::getEndpoints)
                .map(endpoints -> endpoints.get(Endpoint.valueOf(ENDPOINT_AUCTION)))
                .map(EndpointExecutionPlan::getStages)
                .map(stages -> stages.get(Stage.valueOf(stage)))
                .map(StageExecutionPlan::getGroups)
                .orElseGet(List::of)
                .stream()
                .map(ExecutionGroup::getHookSequence)
                .flatMap(java.util.Collection::stream)
                .anyMatch(hook -> hookCode.equals(hook.getHookImplCode()));
    }
}
