package org.prebid.server.activity;

import java.util.Map;
import java.util.Objects;

public class ActivityInfrastructure {

    public static final boolean ALLOW_ACTIVITY_BY_DEFAULT = true;

    private final Map<Activity, ActivityConfiguration> activitiesConfigurations;

    public ActivityInfrastructure(Map<Activity, ActivityConfiguration> activitiesConfigurations) {
        this.activitiesConfigurations = Objects.requireNonNull(activitiesConfigurations);
    }

    public boolean isAllowed(Activity activity, ActivityPayload activityPayload) {
        final ActivityConfiguration activityConfiguration = activitiesConfigurations.get(activity);
        return activityConfiguration != null
                ? activityConfiguration.isAllowed(activityPayload)
                : ALLOW_ACTIVITY_BY_DEFAULT;
    }
}
