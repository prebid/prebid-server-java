package org.prebid.server.activity.infrastructure.creator;

import org.apache.commons.collections4.ListUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.ActivityController;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.debug.ActivityInfrastructureDebug;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeEid;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class ActivityInfrastructureCreator {

    private static final Logger logger = LoggerFactory.getLogger(ActivityInfrastructureCreator.class);

    private final ActivityRuleFactory activityRuleFactory;
    private final Purpose defaultPurpose4;
    private final Metrics metrics;
    private final JacksonMapper jacksonMapper;

    public ActivityInfrastructureCreator(ActivityRuleFactory activityRuleFactory,
                                         GdprConfig gdprConfig,
                                         Metrics metrics,
                                         JacksonMapper jacksonMapper) {

        this.activityRuleFactory = Objects.requireNonNull(activityRuleFactory);
        this.defaultPurpose4 = Optional.ofNullable(gdprConfig)
                .map(GdprConfig::getPurposes)
                .map(Purposes::getP4)
                .orElse(null);
        this.metrics = Objects.requireNonNull(metrics);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
    }

    public ActivityInfrastructure create(Account account, GppContext gppContext, TraceLevel traceLevel) {
        final ActivityInfrastructureDebug debug = debugWheel(account, traceLevel);
        return new ActivityInfrastructure(parse(account, gppContext, debug), debug);
    }

    private ActivityInfrastructureDebug debugWheel(Account account, TraceLevel traceLevel) {
        return new ActivityInfrastructureDebug(account.getId(), traceLevel, metrics, jacksonMapper);
    }

    Map<Activity, ActivityController> parse(Account account, GppContext gppContext, ActivityInfrastructureDebug debug) {
        final Optional<AccountPrivacyConfig> accountPrivacyConfig = Optional.ofNullable(account.getPrivacy());

        final Map<Activity, AccountActivityConfiguration> activitiesConfiguration = accountPrivacyConfig
                .map(AccountPrivacyConfig::getActivities)
                .orElseGet(Collections::emptyMap);
        final Map<PrivacyModuleQualifier, AccountPrivacyModuleConfig> modulesConfigs = accountPrivacyConfig
                .map(AccountPrivacyConfig::getModules)
                .orElseGet(Collections::emptyList)
                .stream()
                .collect(Collectors.toMap(
                        AccountPrivacyModuleConfig::getCode,
                        UnaryOperator.identity(),
                        takeFirstAndLogDuplicates(account.getId())));

        return Arrays.stream(Activity.values()).collect(Collectors.toMap(
                UnaryOperator.identity(),
                fallbackActivity(
                        activitiesConfiguration,
                        accountPrivacyConfig,
                        activity -> from(
                                activity,
                                activitiesConfiguration.get(activity),
                                modulesConfigs,
                                gppContext,
                                debug)),
                (oldValue, newValue) -> oldValue,
                enumMapFactory()));
    }

    private BinaryOperator<AccountPrivacyModuleConfig> takeFirstAndLogDuplicates(String accountId) {
        return (first, second) -> {
            logger.warn("Duplicate configuration found for privacy module %s for account %s"
                    .formatted(second.getCode(), accountId));
            metrics.updateAlertsMetrics(MetricName.general);

            return first;
        };
    }

    // TODO: remove this wrapper after transition period
    private Function<Activity, ActivityController> fallbackActivity(
            Map<Activity, AccountActivityConfiguration> activitiesConfiguration,
            Optional<AccountPrivacyConfig> accountPrivacyConfig,
            Function<Activity, ActivityController> activityControllerCreator) {

        final boolean imitateTransmitEids = !activitiesConfiguration.containsKey(Activity.TRANSMIT_EIDS)
                && activitiesConfiguration.containsKey(Activity.TRANSMIT_UFPD)
                && accountPrivacyConfig
                .map(AccountPrivacyConfig::getGdpr)
                .map(AccountGdprConfig::getPurposes)
                .map(Purposes::getP4)
                .or(() -> Optional.ofNullable(defaultPurpose4))
                .map(Purpose::getEid)
                .map(PurposeEid::getActivityTransition)
                .orElse(true);

        return originalActivity -> originalActivity == Activity.TRANSMIT_EIDS && imitateTransmitEids
                ? activityControllerCreator.apply(Activity.TRANSMIT_UFPD)
                : activityControllerCreator.apply(originalActivity);
    }

    private ActivityController from(Activity activity,
                                    AccountActivityConfiguration activityConfiguration,
                                    Map<PrivacyModuleQualifier, AccountPrivacyModuleConfig> modulesConfigs,
                                    GppContext gppContext,
                                    ActivityInfrastructureDebug debug) {

        if (activityConfiguration == null) {
            return ActivityController.of(
                    ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT,
                    Collections.emptyList(),
                    debug);
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

        return ActivityController.of(allow, rules, debug);
    }

    private static boolean allowFromConfig(Boolean configValue) {
        return configValue != null ? configValue : ActivityInfrastructure.ALLOW_ACTIVITY_BY_DEFAULT;
    }

    private static Supplier<Map<Activity, ActivityController>> enumMapFactory() {
        return () -> new EnumMap<>(Activity.class);
    }
}
