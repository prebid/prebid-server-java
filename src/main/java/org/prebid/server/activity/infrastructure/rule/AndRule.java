package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

import java.util.List;
import java.util.Objects;

public class AndRule implements Rule {

    private final List<Rule> rules;

    public AndRule(List<Rule> rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    @Override
    public Result proceed(ActivityCallPayload activityCallPayload) {
        Result result = Result.ABSTAIN;

        for (Rule rule : rules) {
            Result ruleResult = rule.proceed(activityCallPayload);
            if (ruleResult != Result.ABSTAIN) {
                result = ruleResult;
            }

            if (result == Result.DISALLOW) {
                break;
            }
        }

        return result;
    }
}
