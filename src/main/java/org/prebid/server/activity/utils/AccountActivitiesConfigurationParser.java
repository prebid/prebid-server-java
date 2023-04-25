package org.prebid.server.activity.utils;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ActivityConfiguration;
import org.prebid.server.activity.ActivityInfrastructure;
import org.prebid.server.activity.rule.ConditionalRule;
import org.prebid.server.activity.rule.Rule;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityConditionRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class AccountActivitiesConfigurationParser {

    private AccountActivitiesConfigurationParser() {
    }

    public static Map<Activity, ActivityConfiguration> parse(Account account) {
        final Map<Activity, AccountActivityConfiguration> activitiesConfiguration =
                Optional.ofNullable(account.getPrivacy())
                        .map(AccountPrivacyConfig::getActivities)
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
                .map(AccountActivitiesConfigurationParser::from)
                .toList();

        return ActivityConfiguration.of(allow, rules);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Rule from(AccountActivityRuleConfig accountActivityRuleConfig) {
        if (accountActivityRuleConfig instanceof AccountActivityConditionRuleConfig conditionalRuleConfig) {
            return from(conditionalRuleConfig);
        }

        // should never happen
        throw new AssertionError();
    }

    private static Rule from(AccountActivityConditionRuleConfig conditionalRuleConfig) {
        final boolean allow = allowFromConfig(conditionalRuleConfig.getAllow());
        final Optional<AccountActivityConditionRuleConfig.Condition> condition =
                Optional.ofNullable(conditionalRuleConfig.getCondition());

        return ConditionalRule.of(
                condition.map(AccountActivityConditionRuleConfig.Condition::getComponentTypes).orElse(null),
                condition.map(AccountActivityConditionRuleConfig.Condition::getComponentNames).orElse(null),
                allow);
    }

    private static Supplier<Map<Activity, ActivityConfiguration>> enumMapFactory() {
        return () -> new EnumMap<>(Activity.class);
    }
}
