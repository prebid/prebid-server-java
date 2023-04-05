package org.prebid.server.auction.model.activity;

import org.prebid.server.auction.model.activity.payload.ActivityPayload;
import org.prebid.server.auction.model.activity.rule.Rule;

import java.util.Objects;
import java.util.Queue;

public class ActivityInfrastructure {

    private static final boolean ALLOW_ACTIVITY_BY_DEFAULT = true;

    private final Queue<Rule> rules;

    private ActivityInfrastructure(Queue<Rule> rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    public boolean isAllowed(Activity activity, ActivityPayload activityPayload) {
        return rules.stream()
                .filter(rule -> rule.matches(activity, activityPayload))
                .findFirst()
                .map(Rule::allowed)
                .orElse(ALLOW_ACTIVITY_BY_DEFAULT);
    }
}
