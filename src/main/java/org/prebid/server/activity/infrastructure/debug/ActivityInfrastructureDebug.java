package org.prebid.server.activity.infrastructure.debug;

import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.SkippedPrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.rule.AndRule;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInfrastructure;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocation;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocationDefaultResult;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ActivityInfrastructureDebug {

    private final String accountId;
    private final TraceLevel traceLevel;
    private final List<ExtTraceActivityInfrastructure> traceLog;
    private final Set<PrivacyModuleQualifier> skippedPrivacyModules;
    private final Metrics metrics;
    private final JacksonMapper jacksonMapper;

    public ActivityInfrastructureDebug(String accountId,
                                       TraceLevel traceLevel,
                                       Metrics metrics,
                                       JacksonMapper jacksonMapper) {

        this.accountId = accountId;
        this.traceLevel = traceLevel;
        this.traceLog = new ArrayList<>();
        this.skippedPrivacyModules = EnumSet.noneOf(PrivacyModuleQualifier.class);
        this.metrics = Objects.requireNonNull(metrics);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
    }

    public void emitActivityInvocation(Activity activity, ActivityInvocationPayload activityInvocationPayload) {
        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityInvocation.of(
                    "Invocation of Activity Infrastructure.",
                    activity,
                    activityInvocationPayload));
        }
    }

    public void emitActivityInvocationDefaultResult(boolean defaultResult) {
        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityInvocationDefaultResult.of(
                    "Setting the default invocation result.",
                    defaultResult));
        }
    }

    public void emitProcessedRule(Rule rule, Rule.Result result) {
        collectSkippedPrivacyModules(rule);

        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityRule.of(
                    "Processing rule.",
                    atLeast(TraceLevel.verbose) ? ActivityDebugUtils.asLogEntry(rule, jacksonMapper.mapper()) : null,
                    result));
        }

        metrics.updateRequestsActivityProcessedRulesCount();
        if (atLeast(TraceLevel.verbose)) {
            metrics.updateAccountActivityProcessedRulesCount(accountId);
        }
    }

    private void collectSkippedPrivacyModules(Rule rule) {
        if (rule instanceof SkippedPrivacyModule module) {
            skippedPrivacyModules.add(module.skippedModule());
        } else if (rule instanceof AndRule andRule) {
            andRule.rules().stream()
                    .filter(SkippedPrivacyModule.class::isInstance)
                    .map(SkippedPrivacyModule.class::cast)
                    .forEach(module -> skippedPrivacyModules.add(module.skippedModule()));
        }
    }

    public void emitActivityInvocationResult(Activity activity,
                                             ActivityInvocationPayload activityInvocationPayload,
                                             boolean result) {

        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityInvocationResult.of(
                    "Activity Infrastructure invocation result.",
                    activity,
                    result));
        }

        if (!result) {
            updateActivityMetrics(
                    activity,
                    activityInvocationPayload.componentType(),
                    activityInvocationPayload.componentName());
        }
    }

    public void updateActivityMetrics(Activity activity, ComponentType componentType, String componentName) {
        metrics.updateRequestsActivityDisallowedCount(activity);
        if (atLeast(TraceLevel.verbose)) {
            metrics.updateAccountActivityDisallowedCount(accountId, activity);
        }
        if (componentType == ComponentType.BIDDER) {
            metrics.updateAdapterActivityDisallowedCount(componentName, activity);
        }
    }

    public List<ExtTraceActivityInfrastructure> trace() {
        return Collections.unmodifiableList(traceLog);
    }

    public Set<PrivacyModuleQualifier> skippedPrivacyModules() {
        return Collections.unmodifiableSet(skippedPrivacyModules);
    }

    private boolean atLeast(TraceLevel minTraceLevel) {
        return traceLevel != null && traceLevel.ordinal() >= minTraceLevel.ordinal();
    }
}
