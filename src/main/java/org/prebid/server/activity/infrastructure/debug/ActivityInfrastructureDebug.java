package org.prebid.server.activity.infrastructure.debug;

import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInfrastructure;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocation;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocationDefaultResult;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ActivityInfrastructureDebug {

    private final String accountId;
    private final TraceLevel traceLevel;
    private final List<ExtTraceActivityInfrastructure> traceLog;
    private final Metrics metrics;

    public ActivityInfrastructureDebug(String accountId, TraceLevel traceLevel, Metrics metrics) {
        this.accountId = accountId;
        this.traceLevel = traceLevel;
        this.traceLog = new ArrayList<>();
        this.metrics = Objects.requireNonNull(metrics);
    }

    public void emitActivityInvocation(Activity activity, ActivityInvocationPayload activityInvocationPayload) {
        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityInvocation.of(
                    activity,
                    atLeast(TraceLevel.verbose) ? activityInvocationPayload : null));
        }
    }

    public void emitActivityInvocationDefaultResult(boolean defaultResult) {
        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityInvocationDefaultResult.of(defaultResult));
        }
    }

    public void emitProcessedRule(Rule rule, Rule.Result result) {
        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityRule.of(
                    atLeast(TraceLevel.verbose) && rule instanceof Loggable loggableRule
                            ? loggableRule.asLogEntry()
                            : null,
                    result));
        }

        metrics.updateRequestsActivityProcessedRulesCount();
        if (atLeast(TraceLevel.verbose)) {
            metrics.updateAccountActivityProcessedRulesCount(accountId);
        }
    }

    public void emitActivityInvocationResult(Activity activity,
                                             ActivityInvocationPayload activityInvocationPayload,
                                             boolean result) {

        if (atLeast(TraceLevel.basic)) {
            traceLog.add(ExtTraceActivityInvocationResult.of(activity, result));
        }

        if (!result) {
            metrics.updateRequestsActivityDisallowedCount(activity);
            if (atLeast(TraceLevel.verbose)) {
                metrics.updateAccountActivityDisallowedCount(accountId, activity);
            }
            if (activityInvocationPayload.componentType() == ComponentType.BIDDER) {
                metrics.updateAdapterActivityDisallowedCount(activityInvocationPayload.componentName(), activity);
            }
        }
    }

    public List<ExtTraceActivityInfrastructure> trace() {
        return Collections.unmodifiableList(traceLog);
    }

    private boolean atLeast(TraceLevel minTraceLevel) {
        return traceLevel != null && traceLevel.ordinal() >= minTraceLevel.ordinal();
    }
}
