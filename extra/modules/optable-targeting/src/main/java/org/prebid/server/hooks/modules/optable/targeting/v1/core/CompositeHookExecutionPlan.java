package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.model.Endpoint;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeHookExecutionPlan {

    private static final String ENDPOINT_AUCTION = "openrtb2_auction";
    private static final String STAGE_RAW_AUCTION_REQUEST = "raw_auction_request";
    private static final String STAGE_BIDDER_REQUEST = "bidder_request";
    private static final String HOOK_CODE_OPTABLE_RAW_AUCTION = "optable-targeting-raw-auction-request-hook";
    private static final String HOOK_CODE_OPTABLE_BIDDER_REQUEST = "optable-targeting-bidder-request-hook";

    private final boolean hasGlobalRawAuctionRequestHook;

    private final boolean hasGlobalBidderRequestHook;

    private final ConcurrentHashMap<String, Boolean> rawAuctionRequestHookCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> bidderRequestHookCache = new ConcurrentHashMap<>();

    private CompositeHookExecutionPlan(boolean hasGlobalRawAuctionRequestHook, boolean hasGlobalBidderRequestHook) {
        this.hasGlobalRawAuctionRequestHook = hasGlobalRawAuctionRequestHook;
        this.hasGlobalBidderRequestHook = hasGlobalBidderRequestHook;
    }

    public static CompositeHookExecutionPlan of(ExecutionPlan globalExecutionPlan) {
        return globalExecutionPlan == null
                ? new CompositeHookExecutionPlan(false, false)
                : new CompositeHookExecutionPlan(
                        hasHook(globalExecutionPlan, STAGE_RAW_AUCTION_REQUEST, HOOK_CODE_OPTABLE_RAW_AUCTION),
                        hasHook(globalExecutionPlan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST));
    }

    public boolean hasRawAuctionRequestHook(Account account) {
        final String accountId = account != null ? account.getId() : null;

        return StringUtils.isNotEmpty(accountId)
                ? rawAuctionRequestHookCache.computeIfAbsent(accountId, id -> {
                    final ExecutionPlan accountSpecificHoksExecutionPlan = resolveExecutionPlan(account);
                    return hasHook(
                            accountSpecificHoksExecutionPlan, STAGE_RAW_AUCTION_REQUEST, HOOK_CODE_OPTABLE_RAW_AUCTION)
                            || hasGlobalRawAuctionRequestHook;
                })
                : false;
    }

    public boolean hasBidderRequestHook(Account account) {
        final String accountId = account != null ? account.getId() : null;

        return StringUtils.isNotEmpty(accountId)
                ? bidderRequestHookCache.computeIfAbsent(accountId, id -> {
                    final ExecutionPlan accountSpecificHoksExecutionPlan = resolveExecutionPlan(account);
                    return hasHook(
                            accountSpecificHoksExecutionPlan, STAGE_BIDDER_REQUEST, HOOK_CODE_OPTABLE_BIDDER_REQUEST)
                            || hasGlobalBidderRequestHook;
                })
                : false;
    }

    private ExecutionPlan resolveExecutionPlan(Account account) {
        return Optional.ofNullable(account)
                .map(org.prebid.server.settings.model.Account::getHooks)
                .map(org.prebid.server.settings.model.AccountHooksConfiguration::getExecutionPlan)
                .orElse(null);
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
