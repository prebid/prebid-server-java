package org.prebid.server.activity.infrastructure.creator;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityController;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;

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

    Map<Activity, ActivityController> parse(Account account, GppContext gppContext) {
        final Optional<AccountPrivacyConfig> accountPrivacyConfig = Optional.ofNullable(account)
                .map(Account::getPrivacy);

        final Map<Activity, AccountActivityConfiguration> activitiesConfiguration = accountPrivacyConfig
                .map(AccountPrivacyConfig::getActivities)
                .orElseGet(Collections::emptyMap);
        final List<AccountPrivacyModuleConfig> modulesConfigs = accountPrivacyConfig
                .map(AccountPrivacyConfig::getModules)
                .orElseGet(Collections::emptyList);

        return Arrays.stream(Activity.values())
                .collect(Collectors.toMap(
                        UnaryOperator.identity(),
                        activity -> from(activity, activitiesConfiguration.get(activity), modulesConfigs, gppContext),
                        (oldValue, newValue) -> newValue,
                        enumMapFactory()));
    }

    private ActivityController from(Activity activity,
                                    AccountActivityConfiguration activityConfiguration,
                                    List<AccountPrivacyModuleConfig> modulesConfigs,
                                    GppContext gppContext) {

        if (activityConfiguration == null) {
            return ActivityController.of(ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT, Collections.emptyList());
        }

        final ActivityControllerCreationContext creationContext = ActivityControllerCreationContext.of(
                activity,
                modulesConfigs,
                gppContext);

        final boolean allow = allowFromConfig(activityConfiguration.getAllow());
        final List<Rule> rules = ListUtils.emptyIfNull(activityConfiguration.getRules()).stream()
                .filter(Objects::nonNull)
                .map(ruleConfiguration -> activityRuleFactory.from(ruleConfiguration, creationContext))
                .toList();

        return ActivityController.of(allow, rules);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Supplier<Map<Activity, ActivityController>> enumMapFactory() {
        return () -> new EnumMap<>(Activity.class);
    }
}
