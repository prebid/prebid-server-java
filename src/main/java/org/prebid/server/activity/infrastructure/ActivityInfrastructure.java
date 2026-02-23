
package org.prebid.server.activity.infrastructure;

import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.debug.ActivityInfrastructureDebug;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceActivityInfrastructure;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ActivityInfrastructure {

    public static final boolean ALLOW_ACTIVITY_BY_DEFAULT = true;

    private final Map<Activity, ActivityController> activitiesControllers;
    private final ActivityInfrastructureDebug debug;

    public ActivityInfrastructure(Map<Activity, ActivityController> activitiesControllers,
                                  ActivityInfrastructureDebug debug) {

        validate(activitiesControllers);

        this.activitiesControllers = activitiesControllers;
        this.debug = Objects.requireNonNull(debug);
    }

    private static void validate(Map<Activity, ActivityController> activitiesControllers) {
        if (activitiesControllers == null || activitiesControllers.size() != Activity.values().length) {
            throw new AssertionError("Activities controllers must include all possible activities.");
        }
    }

    public boolean isAllowed(Activity activity, ActivityInvocationPayload activityInvocationPayload) {
        debug.emitActivityInvocation(activity, activityInvocationPayload);
        final boolean result = activitiesControllers.get(activity).isAllowed(activityInvocationPayload);
        debug.emitActivityInvocationResult(activity, activityInvocationPayload, result);

        return result;
    }

    public void updateActivityMetrics(Activity activity, ComponentType componentType, String componentName) {
        debug.updateActivityMetrics(activity, componentType, componentName);
    }

    public List<ExtTraceActivityInfrastructure> debugTrace() {
        return debug.trace();
    }

    public Set<PrivacyModuleQualifier> skippedPrivacyModules() {
        return debug.skippedPrivacyModules();
    }
}
