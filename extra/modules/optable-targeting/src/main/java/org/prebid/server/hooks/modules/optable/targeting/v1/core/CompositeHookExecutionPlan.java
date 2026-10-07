package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableBidderRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableRawAuctionRequestHook;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CompositeHookExecutionPlan {

    private static final HookHttpEndpoint ENDPOINT_AUCTION = HookHttpEndpoint.POST_AUCTION;
    private static final String STAGE_RAW_AUCTION_REQUEST = "raw_auction_request";
    private static final String STAGE_BIDDER_REQUEST = "bidder_request";
    private static final String HOOK_CODE_OPTABLE_RAW_AUCTION = OptableRawAuctionRequestHook.CODE;
    private static final String HOOK_CODE_OPTABLE_BIDDER_REQUEST = OptableBidderRequestHook.CODE;

    private final boolean hasHostRawAuctionRequestHook;

    private final boolean hasHostBidderRequestHook;

    private final long hostBidderRequestHookTimeout;

    private final ExecutionPlan defaultAccountExecutionPlan;

    private final ConcurrentHashMap<String, Boolean> rawAuctionRequestHookCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> bidderRequestHookCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> bidderRequestHookTimeoutCache = new ConcurrentHashMap<>();

    private CompositeHookExecutionPlan(boolean hasHostRawAuctionRequestHook,
                                       boolean hasHostBidderRequestHook,
                                       long hostBidderRequestHookTimeout,
                                       ExecutionPlan defaultAccountExecutionPlan) {

        this.hasHostRawAuctionRequestHook = hasHostRawAuctionRequestHook;
        this.hasHostBidderRequestHook = hasHostBidderRequestHook;
        this.hostBidderRequestHookTimeout = hostBidderRequestHookTimeout;
        this.defaultAccountExecutionPlan = defaultAccountExecutionPlan;
    }

    public static CompositeHookExecutionPlan of(ExecutionPlan hostExecutionPlan,
                                                ExecutionPlan defaultAccountExecutionPlan) {

        return new CompositeHookExecutionPlan(
                hasHook(hostExecutionPlan, STAGE_RAW_AUCTION_REQUEST, HOOK_CODE_OPTABLE_RAW_AUCTION),
                hasHook(hostExecutionPlan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST),
                getHookTimeout(hostExecutionPlan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST),
                defaultAccountExecutionPlan);
    }

    private <T> T computeFromAccount(Account account,
                                     ConcurrentHashMap<String, T> cache,
                                     T defaultValue,
                                     Function<ExecutionPlan, T> compute) {
        final String accountId = account != null ? account.getId() : null;
        return StringUtils.isNotEmpty(accountId)
                ? cache.computeIfAbsent(accountId, id -> compute.apply(resolveExecutionPlan(account)))
                : defaultValue;
    }

    public boolean hasRawAuctionRequestHook(Account account) {
        return computeFromAccount(account, rawAuctionRequestHookCache, false,
                plan -> hasHook(plan, STAGE_RAW_AUCTION_REQUEST, HOOK_CODE_OPTABLE_RAW_AUCTION)
                        || hasHostRawAuctionRequestHook);
    }

    public boolean hasBidderRequestHook(Account account) {
        return computeFromAccount(account, bidderRequestHookCache, false,
                plan -> hasHook(plan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST)
                        || hasHostBidderRequestHook);
    }

    public long getOptableTargetingBidderRequestTimeout(Account account) {
        return computeFromAccount(account, bidderRequestHookTimeoutCache, hostBidderRequestHookTimeout,
                plan -> {
                    final long timeout = getHookTimeout(plan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST);
                    return timeout != 0 ? timeout : hostBidderRequestHookTimeout;
                });
    }

    private ExecutionPlan resolveExecutionPlan(Account account) {
        final ExecutionPlan accountExecutionPlan = Optional.ofNullable(account)
                .map(Account::getHooks)
                .map(AccountHooksConfiguration::getExecutionPlan)
                .orElse(null);

        return accountExecutionPlan != null ? accountExecutionPlan : defaultAccountExecutionPlan;
    }

    private static boolean hasHook(ExecutionPlan executionPlan, String stage, String hookCode) {
        return Optional.ofNullable(executionPlan)
                .map(ExecutionPlan::getEndpoints)
                .map(endpoints -> endpoints.get(ENDPOINT_AUCTION))
                .map(EndpointExecutionPlan::getStages)
                .map(stages -> stages.get(Stage.valueOf(stage)))
                .map(StageExecutionPlan::getGroups)
                .orElseGet(List::of)
                .stream()
                .map(ExecutionGroup::getHookSequence)
                .flatMap(java.util.Collection::stream)
                .anyMatch(hook -> hookCode.equals(hook.getHookImplCode()));
    }

    private static long getHookTimeout(ExecutionPlan executionPlan, String stage, String hookCode) {
        return Optional.ofNullable(executionPlan)
                .map(ExecutionPlan::getEndpoints)
                .map(endpoints -> endpoints.get(ENDPOINT_AUCTION))
                .map(EndpointExecutionPlan::getStages)
                .map(stages -> stages.get(Stage.valueOf(stage)))
                .map(StageExecutionPlan::getGroups)
                .orElseGet(List::of)
                .stream().findFirst()
                .map(ExecutionGroup::getTimeout)
                .orElse(0L);
    }
}
