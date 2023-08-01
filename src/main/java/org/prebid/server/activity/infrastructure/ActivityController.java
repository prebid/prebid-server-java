package org.prebid.server.activity.infrastructure;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.rule.Rule;

import java.util.List;
import java.util.Objects;

public class ActivityController {

    private final boolean allowByDefault;
    private final List<Rule> rules;
    private final ActivityInfrastructureDebug debug;

    private ActivityController(boolean allowByDefault, List<Rule> rules, ActivityInfrastructureDebug debug) {
        this.allowByDefault = allowByDefault;
        this.rules = Objects.requireNonNull(rules);
        this.debug = Objects.requireNonNull(debug);
    }

    public static ActivityController of(boolean allowByDefault, List<Rule> rules, ActivityInfrastructureDebug debug) {
        return new ActivityController(allowByDefault, rules, debug);
    }

    public boolean isAllowed(ActivityCallPayload activityCallPayload) {
        boolean result = allowByDefault;

        for (Rule rule : rules) {
            final Rule.Result ruleResult = rule.proceed(activityCallPayload);
            debug.emitProcessedRule(rule, ruleResult);

            if (ruleResult != Rule.Result.ABSTAIN) {
                result = ruleResult == Rule.Result.ALLOW;
                break;
            }
        }

        return result;
    }
}
