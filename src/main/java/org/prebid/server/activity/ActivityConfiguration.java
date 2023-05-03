package org.prebid.server.activity;

import org.prebid.server.activity.rule.Rule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ActivityConfiguration {

    private final boolean allowByDefault;
    private final List<Rule> rules;

    private ActivityConfiguration(boolean allowByDefault, List<Rule> rules) {
        this.allowByDefault = allowByDefault;
        this.rules = Objects.requireNonNull(rules);
    }

    public static ActivityConfiguration of(boolean allowByDefault, List<Rule> rules) {
        return new ActivityConfiguration(allowByDefault, rules);
    }

    public ActivityContextResult isAllowed(ActivityPayload activityPayload) {
        int processedRulesCount = 0;
        Rule matchedRule = null;

        for (Rule rule : rules) {
            processedRulesCount++;
            if (rule.matches(activityPayload)) {
                matchedRule = rule;
                break;
            }
        }

        return ActivityContextResult.of(
                Optional.ofNullable(matchedRule)
                        .map(Rule::allowed)
                        .orElse(allowByDefault),
                processedRulesCount);
    }
}
