package org.prebid.server.auction.model.activity;

import java.util.EnumMap;

public class ActivityInfrastructure {

    private static final int MIN_PRIORITY_VALUE = 1;
    private static final int DEFAULT_PRIORITY_VALUE = 10;
    private static final int MAX_PRIORITY_VALUE = 100;

    private final EnumMap<Activity, ActivityContext> activitiesContexts = new EnumMap<>(Activity.class);

    private ActivityInfrastructure() {
    }

    public static ActivityInfrastructure create() {
        return new ActivityInfrastructure();
    }

    public void addRule(Activity activity, Rule rule) {
        addRule(activity, DEFAULT_PRIORITY_VALUE, rule);
    }

    public void addRule(Activity activity, int priority, Rule rule) {
        activitiesContexts
                .computeIfAbsent(activity, key -> new ActivityContext())
                .addRule(roundPriority(priority), rule);
    }

    private static int roundPriority(int priority) {
        return Math.max(Math.min(priority, MAX_PRIORITY_VALUE), MIN_PRIORITY_VALUE);
    }

    public boolean isAllowed(Activity activity, Object value) {
        final ActivityContext activityContext = activitiesContexts.get(activity);
        return activityContext != null
                ? activityContext.isAllowed(value)
                : ActivityContext.ALLOW_ACTIVITY_BY_DEFAULT;
    }
}
