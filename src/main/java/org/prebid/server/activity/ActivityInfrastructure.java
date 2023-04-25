
package org.prebid.server.activity;

import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;

import java.util.Map;
import java.util.Objects;

public class ActivityInfrastructure {

    public static final boolean ALLOW_ACTIVITY_BY_DEFAULT = true;
    private static final ActivityContextResult ALLOW_ACTIVITY_BY_DEFAULT_RESULT =
            ActivityContextResult.of(ALLOW_ACTIVITY_BY_DEFAULT, 0);

    private final String accountId;
    private final Map<Activity, ActivityConfiguration> activitiesConfigurations;
    private final TraceLevel traceLevel;
    private final Metrics metrics;

    public ActivityInfrastructure(String accountId,
                                  Map<Activity, ActivityConfiguration> activitiesConfigurations,
                                  TraceLevel traceLevel,
                                  Metrics metrics) {

        this.accountId = Objects.requireNonNull(accountId);
        this.activitiesConfigurations = Objects.requireNonNull(activitiesConfigurations);
        this.traceLevel = Objects.requireNonNull(traceLevel);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public boolean isAllowed(Activity activity, ComponentType componentType, String componentName) {
        final ActivityPayload activityPayload = ActivityPayload.of(componentType, componentName);

        final ActivityConfiguration activityConfiguration = activitiesConfigurations.get(activity);
        final ActivityContextResult result = activityConfiguration != null
                ? activityConfiguration.isAllowed(activityPayload)
                : ALLOW_ACTIVITY_BY_DEFAULT_RESULT;

        updateMetrics(activity, activityPayload, result);

        return result.isAllowed();
    }

    private void updateMetrics(Activity activity, ActivityPayload activityPayload, ActivityContextResult result) {
        final int processedRulesCount = result.getProcessedRulesCount();
        if (processedRulesCount > 0) {
            metrics.updateRequestsActivityProcessedRulesCount(processedRulesCount);
            metrics.updateAccountActivityProcessedRulesCount(accountId, processedRulesCount);
        }

        if (!result.isAllowed()) {
            metrics.updateRequestsActivityDisallowedCount(activity);
            if (traceLevel == TraceLevel.verbose) {
                metrics.updateAccountActivityDisallowedCount(accountId, activity);
            }
            if (activityPayload.getComponentType() == ComponentType.BIDDER) {
                metrics.updateAdapterActivityDisallowedCount(activityPayload.getComponentName(), activity);
            }
        }
    }
}
