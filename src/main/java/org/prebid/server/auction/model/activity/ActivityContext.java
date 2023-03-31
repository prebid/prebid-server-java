package org.prebid.server.auction.model.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ActivityContext {

    public static final boolean ALLOW_ACTIVITY_BY_DEFAULT = true;

    private final Map<Integer, List<Rule>> prioritizedRules = new TreeMap<>();

    public void addRule(int priority, Rule rule) {
        prioritizedRules
                .computeIfAbsent(priority, key -> new ArrayList<>())
                .add(rule);
    }

    public boolean isAllowed(Object value) {
        return prioritizedRules.values().stream()
                .map(rules -> isAllowed(rules, value))
                .filter(ruleResult -> ruleResult != RuleResult.NOT_MATCHED)
                .findFirst()
                .map(ruleResult -> ruleResult == RuleResult.ALLOWED)
                .orElse(ALLOW_ACTIVITY_BY_DEFAULT);
    }

    private static RuleResult isAllowed(List<Rule> rules, Object value) {
        RuleResult result = RuleResult.NOT_MATCHED;
        for (Rule rule : rules) {
            if (!rule.matches(value)) {
                continue;
            }

            if (!rule.allowed()) {
                result = RuleResult.DISALLOWED;
                break;
            }

            result = RuleResult.ALLOWED;
        }

        return result;
    }

    private enum RuleResult {
        ALLOWED, DISALLOWED, NOT_MATCHED
    }
}
