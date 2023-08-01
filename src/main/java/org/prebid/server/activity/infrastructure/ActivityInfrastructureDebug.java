package org.prebid.server.activity.infrastructure;

import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.Objects;

public class ActivityInfrastructureDebug {

    private final String accountId;
    private final TraceLevel traceLevel;
    private final Metrics metrics;

    public ActivityInfrastructureDebug(String accountId, TraceLevel traceLevel, Metrics metrics) {
        this.accountId = accountId;
        this.traceLevel = traceLevel;
        this.metrics = Objects.requireNonNull(metrics);
    }

    public void emitProcessedRule(Rule rule, Rule.Result result) {
        updateProcessedRuleMetrics();
    }

    private void updateProcessedRuleMetrics() {
        metrics.updateRequestsActivityProcessedRulesCount();
        if (atLeast(TraceLevel.verbose)) {
            metrics.updateAccountActivityProcessedRulesCount(accountId);
        }
    }

    public void emitProcessedActivity(Activity activity,
                                      ActivityCallPayload activityCallPayload,
                                      boolean result) {

        if (!result) {
            updateDisallowedActivityMetrics(activity, activityCallPayload);
        }
    }

    private void updateDisallowedActivityMetrics(Activity activity, ActivityCallPayload activityCallPayload) {
        metrics.updateRequestsActivityDisallowedCount(activity);
        if (atLeast(TraceLevel.verbose)) {
            metrics.updateAccountActivityDisallowedCount(accountId, activity);
        }
        if (activityCallPayload.componentType() == ComponentType.BIDDER) {
            metrics.updateAdapterActivityDisallowedCount(activityCallPayload.componentName(), activity);
        }
    }

    private boolean atLeast(TraceLevel minTraceLevel) {
        return traceLevel != null && traceLevel.ordinal() >= minTraceLevel.ordinal();
    }
}
