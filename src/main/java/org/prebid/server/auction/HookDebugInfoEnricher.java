package org.prebid.server.auction;

import com.iab.openrtb.response.BidResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsActivity;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsAppliedTo;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceGroup;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStage;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStageOutcome;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HookDebugInfoEnricher {

    private HookDebugInfoEnricher() {
    }

    public static AuctionContext enrichWithHooksDebugInfo(AuctionContext context) {
        final ExtModules extModules = toExtModules(context);

        if (extModules == null) {
            return context;
        }

        final BidResponse bidResponse = context.getBidResponse();
        final Optional<ExtBidResponse> ext = Optional.ofNullable(bidResponse.getExt());
        final Optional<ExtBidResponsePrebid> extPrebid = ext.map(ExtBidResponse::getPrebid);

        final ExtBidResponsePrebid updatedExtPrebid = extPrebid
                .map(ExtBidResponsePrebid::toBuilder)
                .orElse(ExtBidResponsePrebid.builder())
                .modules(extModules)
                .build();

        final ExtBidResponse updatedExt = ext
                .map(ExtBidResponse::toBuilder)
                .orElse(ExtBidResponse.builder())
                .prebid(updatedExtPrebid)
                .build();

        final BidResponse updatedBidResponse = bidResponse.toBuilder().ext(updatedExt).build();
        return context.with(updatedBidResponse);
    }

    private static ExtModules toExtModules(AuctionContext context) {
        final Map<String, Map<String, List<String>>> errors =
                toHookMessages(context, HookExecutionOutcome::getErrors);
        final Map<String, Map<String, List<String>>> warnings =
                toHookMessages(context, HookExecutionOutcome::getWarnings);
        final ExtModulesTrace trace = toHookTrace(context);
        return ObjectUtils.anyNotNull(errors, warnings, trace) ? ExtModules.of(errors, warnings, trace) : null;
    }

    private static Map<String, Map<String, List<String>>> toHookMessages(
            AuctionContext context,
            Function<HookExecutionOutcome, List<String>> messagesGetter) {

        if (!context.getDebugContext().isDebugEnabled()) {
            return null;
        }

        final Map<String, List<HookExecutionOutcome>> hookOutcomesByModule =
                context.getHookExecutionContext().getStageOutcomes().values().stream()
                        .flatMap(Collection::stream)
                        .flatMap(stageOutcome -> stageOutcome.getGroups().stream())
                        .flatMap(groupOutcome -> groupOutcome.getHooks().stream())
                        .filter(hookOutcome -> CollectionUtils.isNotEmpty(messagesGetter.apply(hookOutcome)))
                        .collect(Collectors.groupingBy(
                                hookOutcome -> hookOutcome.getHookId().getModuleCode()));

        final Map<String, Map<String, List<String>>> messagesByModule = hookOutcomesByModule.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        outcomes -> outcomes.getValue().stream()
                                .collect(Collectors.groupingBy(
                                        hookOutcome -> hookOutcome.getHookId().getHookImplCode()))
                                .entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        messagesLists -> messagesLists.getValue().stream()
                                                .map(messagesGetter)
                                                .flatMap(Collection::stream)
                                                .toList()))));

        return !messagesByModule.isEmpty() ? messagesByModule : null;
    }

    private static ExtModulesTrace toHookTrace(AuctionContext context) {
        final TraceLevel traceLevel = context.getDebugContext().getTraceLevel();

        if (traceLevel == null) {
            return null;
        }

        final List<ExtModulesTraceStage> stages = context.getHookExecutionContext().getStageOutcomes()
                .entrySet().stream()
                .map(stageOutcome -> toTraceStage(stageOutcome.getKey(), stageOutcome.getValue(), traceLevel))
                .filter(Objects::nonNull)
                .toList();

        if (stages.isEmpty()) {
            return null;
        }

        final long executionTime = stages.stream().mapToLong(ExtModulesTraceStage::getExecutionTime).sum();
        return ExtModulesTrace.of(executionTime, stages);
    }

    private static ExtModulesTraceStage toTraceStage(Stage stage,
                                                     List<StageExecutionOutcome> stageOutcomes,
                                                     TraceLevel level) {

        final List<ExtModulesTraceStageOutcome> extStageOutcomes = stageOutcomes.stream()
                .map(stageOutcome -> toTraceStageOutcome(stageOutcome, level))
                .filter(Objects::nonNull)
                .toList();

        if (extStageOutcomes.isEmpty()) {
            return null;
        }

        final long executionTime = extStageOutcomes.stream()
                .mapToLong(ExtModulesTraceStageOutcome::getExecutionTime)
                .max()
                .orElse(0L);

        return ExtModulesTraceStage.of(stage, executionTime, extStageOutcomes);
    }

    private static ExtModulesTraceStageOutcome toTraceStageOutcome(
            StageExecutionOutcome stageOutcome, TraceLevel level) {

        final List<ExtModulesTraceGroup> groups = stageOutcome.getGroups().stream()
                .map(group -> toTraceGroup(group, level))
                .toList();

        if (groups.isEmpty()) {
            return null;
        }

        final long executionTime = groups.stream().mapToLong(ExtModulesTraceGroup::getExecutionTime).sum();
        return ExtModulesTraceStageOutcome.of(stageOutcome.getEntity(), executionTime, groups);
    }

    private static ExtModulesTraceGroup toTraceGroup(GroupExecutionOutcome group, TraceLevel level) {
        final List<ExtModulesTraceInvocationResult> invocationResults = group.getHooks().stream()
                .map(hook -> toTraceInvocationResult(hook, level))
                .toList();

        final long executionTime = invocationResults.stream()
                .mapToLong(ExtModulesTraceInvocationResult::getExecutionTime)
                .max()
                .orElse(0L);

        return ExtModulesTraceGroup.of(executionTime, invocationResults);
    }

    private static ExtModulesTraceInvocationResult toTraceInvocationResult(HookExecutionOutcome hook,
                                                                           TraceLevel level) {
        return ExtModulesTraceInvocationResult.builder()
                .hookId(hook.getHookId())
                .executionTime(hook.getExecutionTime())
                .status(hook.getStatus())
                .message(hook.getMessage())
                .action(hook.getAction())
                .debugMessages(level == TraceLevel.verbose ? hook.getDebugMessages() : null)
                .analyticsTags(level == TraceLevel.verbose ? toTraceAnalyticsTags(hook.getAnalyticsTags()) : null)
                .build();
    }

    private static ExtModulesTraceAnalyticsTags toTraceAnalyticsTags(Tags analyticsTags) {
        if (analyticsTags == null) {
            return null;
        }

        return ExtModulesTraceAnalyticsTags.of(CollectionUtils.emptyIfNull(analyticsTags.activities()).stream()
                .filter(Objects::nonNull)
                .map(HookDebugInfoEnricher::toTraceAnalyticsActivity)
                .toList());
    }

    private static ExtModulesTraceAnalyticsActivity toTraceAnalyticsActivity(
            Activity activity) {

        return ExtModulesTraceAnalyticsActivity.of(
                activity.name(),
                activity.status(),
                CollectionUtils.emptyIfNull(activity.results()).stream()
                        .filter(Objects::nonNull)
                        .map(HookDebugInfoEnricher::toTraceAnalyticsResult)
                        .toList());
    }

    private static ExtModulesTraceAnalyticsResult toTraceAnalyticsResult(Result result) {
        final AppliedTo appliedTo = result.appliedTo();
        final ExtModulesTraceAnalyticsAppliedTo extAppliedTo = appliedTo != null
                ? ExtModulesTraceAnalyticsAppliedTo.builder()
                .impIds(appliedTo.impIds())
                .bidders(appliedTo.bidders())
                .request(appliedTo.request() ? Boolean.TRUE : null)
                .response(appliedTo.response() ? Boolean.TRUE : null)
                .bidIds(appliedTo.bidIds())
                .build()
                : null;

        return ExtModulesTraceAnalyticsResult.of(result.status(), result.values(), extAppliedTo);
    }

    public static List<ExtAnalyticsTags> toExtAnalyticsTags(AuctionContext context) {
        return context.getHookExecutionContext().getStageOutcomes().entrySet().stream()
                .flatMap(stageToExecutionOutcome -> stageToExecutionOutcome.getValue().stream()
                        .map(StageExecutionOutcome::getGroups)
                        .flatMap(Collection::stream)
                        .map(GroupExecutionOutcome::getHooks)
                        .flatMap(Collection::stream)
                        .filter(hookExecutionOutcome -> hookExecutionOutcome.getAnalyticsTags() != null)
                        .map(hookExecutionOutcome -> ExtAnalyticsTags.of(
                                stageToExecutionOutcome.getKey(),
                                hookExecutionOutcome.getHookId().getModuleCode(),
                                toTraceAnalyticsTags(hookExecutionOutcome.getAnalyticsTags()))))
                .toList();
    }
}
