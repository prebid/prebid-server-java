package org.prebid.server.activity;

import org.prebid.server.activity.rule.Rule;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

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
        final AtomicInteger processedRulesCounter = new AtomicInteger();
        final boolean allowed = rules.stream()
                .peek(rule -> processedRulesCounter.incrementAndGet())
                .filter(rule -> rule.matches(activityPayload))
                .findFirst()
                .map(Rule::allowed)
                .orElse(allowByDefault);

        return ActivityContextResult.of(allowed, processedRulesCounter.get());
    }
}
