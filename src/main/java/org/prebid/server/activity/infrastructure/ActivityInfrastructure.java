
package org.prebid.server.activity.infrastructure;

import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

import java.util.Map;
import java.util.Objects;

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

    public boolean isAllowed(Activity activity, ActivityCallPayload activityCallPayload) {
        final boolean result = activitiesControllers.get(activity).isAllowed(activityCallPayload);
        debug.emitProcessedActivity(activity, activityCallPayload, result);

        return result;
    }
}
