package org.prebid.server.activity;

import org.prebid.server.activity.rule.Rule;

import java.util.List;

public class ActivityConfiguration {

    private final boolean allowByDefault;
    private final List<Rule> rules;

    private ActivityConfiguration(boolean allowByDefault, List<Rule> rules) {
        this.allowByDefault = allowByDefault;
        this.rules = rules;
    }

    public static ActivityConfiguration of(boolean allowByDefault, List<Rule> rules) {
        return new ActivityConfiguration(allowByDefault, rules);
    }

    public boolean isAllowed(ActivityPayload activityPayload) {
        return rules.stream()
                .filter(rule -> rule.matches(activityPayload))
                .findFirst()
                .map(Rule::allowed)
                .orElse(allowByDefault);
    }
}
