package org.prebid.server.activity.utils;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ActivityConfiguration;
import org.prebid.server.activity.ActivityInfrastructure;
import org.prebid.server.activity.rule.ComponentRule;
import org.prebid.server.activity.rule.Rule;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountConsentConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class AccountActivitiesConfigurationUtils {

    private AccountActivitiesConfigurationUtils() {
    }

    public static Map<Activity, ActivityConfiguration> parse(Account account) {
        final Map<Activity, AccountActivityConfiguration> activitiesConfiguration =
                Optional.ofNullable(account)
                        .map(Account::getConsent)
                        .map(AccountConsentConfig::getActivities)
                        .orElse(Collections.emptyMap());

        return Arrays.stream(Activity.values())
                .collect(Collectors.toMap(
                        UnaryOperator.identity(),
                        activity -> from(activitiesConfiguration.get(activity)),
                        (oldValue, newValue) -> newValue,
                        enumMapFactory()));
    }

    private static ActivityConfiguration from(AccountActivityConfiguration accountActivityConfiguration) {
        if (accountActivityConfiguration == null) {
            return ActivityConfiguration.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, Collections.emptyList());
        }

        final boolean allow = allowFromConfig(accountActivityConfiguration.getAllow());
        final List<Rule> rules = ListUtils.emptyIfNull(accountActivityConfiguration.getRules()).stream()
                .filter(Objects::nonNull)
                .map(AccountActivitiesConfigurationUtils::from)
                .toList();

        return ActivityConfiguration.of(allow, rules);
    }

    private static Rule from(AccountActivityRuleConfig accountActivityRuleConfig) {
        if (accountActivityRuleConfig instanceof AccountActivityComponentRuleConfig conditionalRuleConfig) {
            return from(conditionalRuleConfig);
        }

        // should never happen
        throw new AssertionError();
    }

    private static Rule from(AccountActivityComponentRuleConfig conditionalRuleConfig) {
        final boolean allow = allowFromConfig(conditionalRuleConfig.getAllow());
        final AccountActivityComponentRuleConfig.Condition condition = conditionalRuleConfig.getCondition();

        return ComponentRule.of(
                condition != null ? condition.getComponentTypes() : null,
                condition != null ? condition.getComponentNames() : null,
                allow);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Supplier<Map<Activity, ActivityConfiguration>> enumMapFactory() {
        return () -> new EnumMap<>(Activity.class);
    }

    public static boolean isInvalidActivitiesConfiguration(Account account) {
        return Optional.ofNullable(account)
                .map(Account::getConsent)
                .map(AccountConsentConfig::getActivities)
                .stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .anyMatch(AccountActivitiesConfigurationUtils::containsInvalidRule);
    }

    private static boolean containsInvalidRule(AccountActivityConfiguration accountActivityConfiguration) {
        return Optional.ofNullable(accountActivityConfiguration)
                .map(AccountActivityConfiguration::getRules)
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(AccountActivitiesConfigurationUtils::isInvalidConditionRule);
    }

    private static boolean isInvalidConditionRule(AccountActivityRuleConfig rule) {
        if (rule instanceof AccountActivityComponentRuleConfig conditionRule) {
            final AccountActivityComponentRuleConfig.Condition condition = conditionRule.getCondition();
            return condition != null && isInvalidCondition(condition);
        }

        return false;
    }

    private static boolean isInvalidCondition(AccountActivityComponentRuleConfig.Condition condition) {
        return isEmptyNotNull(condition.getComponentTypes()) || isEmptyNotNull(condition.getComponentNames());
    }

    private static <E> boolean isEmptyNotNull(Collection<E> collection) {
        return collection != null && collection.isEmpty();
    }

    public static Map<Activity, AccountActivityConfiguration> removeInvalidRules(
            Map<Activity, AccountActivityConfiguration> activitiesConfiguration) {

        return activitiesConfiguration.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> AccountActivitiesConfigurationUtils.removeInvalidRules(entry.getValue())));
    }

    private static AccountActivityConfiguration removeInvalidRules(AccountActivityConfiguration activityConfiguration) {
        if (!containsInvalidRule(activityConfiguration)) {
            return activityConfiguration;
        }

        return AccountActivityConfiguration.of(
                activityConfiguration.getAllow(),
                activityConfiguration.getRules().stream()
                        .map(rule -> !isInvalidConditionRule(rule) ? rule : null)
                        .filter(Objects::nonNull)
                        .toList());
    }
}
