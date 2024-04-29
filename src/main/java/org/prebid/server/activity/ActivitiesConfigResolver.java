package org.prebid.server.activity;

import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ActivitiesConfigResolver {

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(ActivitiesConfigResolver.class));

    private final double logSamplingRate;

    public ActivitiesConfigResolver(double logSamplingRate) {
        this.logSamplingRate = logSamplingRate;
    }

    public Account resolve(Account account) {
        if (!isInvalidActivitiesConfiguration(account)) {
            return account;
        }

        conditionalLogger.warn(
                "Activity configuration for account %s contains conditional rule with empty array."
                        .formatted(account.getId()),
                logSamplingRate);

        final AccountPrivacyConfig accountPrivacyConfig = account.getPrivacy();
        return account.toBuilder()
                .privacy(accountPrivacyConfig.toBuilder()
                        .activities(removeInvalidRules(accountPrivacyConfig.getActivities()))
                        .build())
                .build();
    }

    private static boolean isInvalidActivitiesConfiguration(Account account) {
        return Optional.ofNullable(account)
                .map(Account::getPrivacy)
                .map(AccountPrivacyConfig::getActivities)
                .stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .anyMatch(ActivitiesConfigResolver::containsInvalidRule);
    }

    private static boolean containsInvalidRule(AccountActivityConfiguration accountActivityConfiguration) {
        return Optional.ofNullable(accountActivityConfiguration)
                .map(AccountActivityConfiguration::getRules)
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(ActivitiesConfigResolver::isInvalidConditionRule);
    }

    private static boolean isInvalidConditionRule(AccountActivityRuleConfig rule) {
        if (rule instanceof AccountActivityComponentRuleConfig conditionRule) {
            final AccountActivityComponentRuleConfig.Condition condition = conditionRule.getCondition();
            return condition != null && isInvalidCondition(condition);
        }

        if (rule instanceof AccountActivityGeoRuleConfig geoRule) {
            final AccountActivityGeoRuleConfig.Condition condition = geoRule.getCondition();
            return condition != null && isInvalidCondition(condition);
        }

        return false;
    }

    private static boolean isInvalidCondition(AccountActivityComponentRuleConfig.Condition condition) {
        return isEmptyNotNull(condition.getComponentTypes()) || isEmptyNotNull(condition.getComponentNames());
    }

    private static boolean isInvalidCondition(AccountActivityGeoRuleConfig.Condition condition) {
        return isEmptyNotNull(condition.getComponentTypes()) || isEmptyNotNull(condition.getComponentNames());
    }

    private static <E> boolean isEmptyNotNull(Collection<E> collection) {
        return collection != null && collection.isEmpty();
    }

    private static Map<Activity, AccountActivityConfiguration> removeInvalidRules(
            Map<Activity, AccountActivityConfiguration> activitiesConfiguration) {

        return activitiesConfiguration.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> removeInvalidRules(entry.getValue())));
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
