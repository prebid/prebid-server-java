package org.prebid.server.activity.infrastructure.creator;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityConfiguration;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;

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

public class ActivityInfrastructureCreator {

    private final ActivityRuleFactory activityRuleFactory;
    private final Metrics metrics;

    public ActivityInfrastructureCreator(ActivityRuleFactory activityRuleFactory, Metrics metrics) {
        this.activityRuleFactory = Objects.requireNonNull(activityRuleFactory);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public ActivityInfrastructure create(Account account, GppContext gppContext, TraceLevel traceLevel) {
        return new ActivityInfrastructure(
                account.getId(),
                parse(account, gppContext),
                traceLevel,
                metrics);
    }

    Map<Activity, ActivityConfiguration> parse(Account account, GppContext gppContext) {
        final Map<Activity, AccountActivityConfiguration> activitiesConfiguration =
                Optional.ofNullable(account)
                        .map(Account::getPrivacy)
                        .map(AccountPrivacyConfig::getActivities)
                        .orElse(Collections.emptyMap());

        return Arrays.stream(Activity.values())
                .collect(Collectors.toMap(
                        UnaryOperator.identity(),
                        activity -> from(activitiesConfiguration.get(activity), gppContext),
                        (oldValue, newValue) -> newValue,
                        enumMapFactory()));
    }

    private ActivityConfiguration from(AccountActivityConfiguration activityConfiguration, GppContext gppContext) {
        if (activityConfiguration == null) {
            return ActivityConfiguration.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, Collections.emptyList());
        }

        final boolean allow = allowFromConfig(activityConfiguration.getAllow());
        final List<Rule> rules = ListUtils.emptyIfNull(activityConfiguration.getRules()).stream()
                .filter(Objects::nonNull)
                .map(ruleConfiguration -> activityRuleFactory.from(ruleConfiguration, gppContext))
                .toList();

        return ActivityConfiguration.of(allow, rules);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Supplier<Map<Activity, ActivityConfiguration>> enumMapFactory() {
        return () -> new EnumMap<>(Activity.class);
    }
}
