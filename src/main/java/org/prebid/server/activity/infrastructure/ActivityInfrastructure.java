
package org.prebid.server.activity.infrastructure;

import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.Map;
import java.util.Objects;

public class ActivityInfrastructure {

    public static final boolean ALLOW_ACTIVITY_BY_DEFAULT = true;

    private final String accountId;
    private final Map<Activity, ActivityController> activitiesControllers;
    private final TraceLevel traceLevel;
    private final Metrics metrics;

    public ActivityInfrastructure(String accountId,
                                  Map<Activity, ActivityController> activitiesControllers,
                                  TraceLevel traceLevel,
                                  Metrics metrics) {

        validate(activitiesControllers);

        this.accountId = accountId;
        this.activitiesControllers = activitiesControllers;
        this.traceLevel = Objects.requireNonNull(traceLevel);
        this.metrics = Objects.requireNonNull(metrics);
    }

    private static void validate(Map<Activity, ActivityController> activitiesControllers) {
        if (activitiesControllers == null || activitiesControllers.size() != Activity.values().length) {
            throw new AssertionError("Activities controllers must include all possible activities.");
        }
    }

    public boolean isAllowed(Activity activity, ActivityCallPayload activityCallPayload) {
        final ActivityCallResult result = activitiesControllers.get(activity).isAllowed(activityCallPayload);
        updateMetrics(activity, activityCallPayload, result);
        return result.isAllowed();
    }

    private void updateMetrics(Activity activity, ActivityCallPayload activityCallPayload, ActivityCallResult result) {
        final int processedRulesCount = result.getProcessedRulesCount();
        if (processedRulesCount > 0) {
            metrics.updateRequestsActivityProcessedRulesCount(processedRulesCount);
            if (traceLevel == TraceLevel.verbose) {
                metrics.updateAccountActivityProcessedRulesCount(accountId, processedRulesCount);
            }
        }

        if (!result.isAllowed()) {
            metrics.updateRequestsActivityDisallowedCount(activity);
            if (traceLevel == TraceLevel.verbose) {
                metrics.updateAccountActivityDisallowedCount(accountId, activity);
            }
            if (activityCallPayload.componentType() == ComponentType.BIDDER) {
                metrics.updateAdapterActivityDisallowedCount(activityCallPayload.componentName(), activity);
            }
        }
    }
}
