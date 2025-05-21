package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.Collection;
import java.util.Optional;

public class ExecutionTimeResolver {

    public long extractOptableTargetingExecutionTime(AuctionInvocationContext invocationContext) {
        return Optional.ofNullable(invocationContext.auctionContext())
                .map(AuctionContext::getHookExecutionContext)
                .map(HookExecutionContext::getStageOutcomes)
                .map(stages -> stages.get(Stage.processed_auction_request))
                .stream()
                .flatMap(Collection::stream)
                .filter(stageExecutionOutcome -> "auction-request".equals(stageExecutionOutcome.getEntity()))
                .map(StageExecutionOutcome::getGroups)
                .flatMap(Collection::stream)
                .map(GroupExecutionOutcome::getHooks)
                .flatMap(Collection::stream)
                .filter(hook -> OptableTargetingModule.CODE.equals(hook.getHookId().getModuleCode()))
                .filter(hook ->
                        OptableTargetingProcessedAuctionRequestHook.CODE.equals(hook.getHookId().getHookImplCode()))
                .findFirst()
                .map(HookExecutionOutcome::getExecutionTime)
                .orElse(0L);
    }
}
