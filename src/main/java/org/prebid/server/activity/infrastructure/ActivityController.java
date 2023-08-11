package org.prebid.server.activity.infrastructure;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.rule.Rule;

import java.util.List;
import java.util.Objects;

public class ActivityController {

    private final boolean allowByDefault;
    private final List<Rule> rules;

    private ActivityController(boolean allowByDefault, List<Rule> rules) {
        this.allowByDefault = allowByDefault;
        this.rules = Objects.requireNonNull(rules);
    }

    public static ActivityController of(boolean allowByDefault, List<Rule> rules) {
        return new ActivityController(allowByDefault, rules);
    }

    public ActivityCallResult isAllowed(ActivityCallPayload activityCallPayload) {
        int processedRulesCount = 0;
        boolean result = allowByDefault;

        for (Rule rule : rules) {
            processedRulesCount++;

            final Rule.Result ruleResult = rule.proceed(activityCallPayload);
            if (ruleResult != Rule.Result.ABSTAIN) {
                result = ruleResult == Rule.Result.ALLOW;
                break;
            }
        }

        return ActivityCallResult.of(result, processedRulesCount);
    }
}
