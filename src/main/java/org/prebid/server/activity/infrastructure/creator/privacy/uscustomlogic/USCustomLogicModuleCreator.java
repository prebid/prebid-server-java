package org.prebid.server.activity.infrastructure.creator.privacy.uscustomlogic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.gpp.encoder.GppModel;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.PrivacySection;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicDataSupplier;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.USCustomLogicModule;
import org.prebid.server.activity.infrastructure.rule.AndRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JsonLogic;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.model.activity.privacy.AccountUSCustomLogicModuleConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class USCustomLogicModuleCreator implements PrivacyModuleCreator {

    private static final Set<Integer> ALLOWED_SECTIONS_IDS =
            PrivacySection.US_PRIVACY_SECTIONS.stream()
                    .map(PrivacySection::sectionId)
                    .collect(Collectors.toSet());

    private final USCustomLogicGppReaderFactory gppReaderFactory;
    private final JsonLogic jsonLogic;
    private final Map<String, JsonLogicNode> jsonLogicNodesCache;
    private final Metrics metrics;

    public USCustomLogicModuleCreator(USCustomLogicGppReaderFactory gppReaderFactory,
                                      JsonLogic jsonLogic,
                                      Integer cacheTtl,
                                      Integer cacheSize,
                                      Metrics metrics) {

        this.gppReaderFactory = Objects.requireNonNull(gppReaderFactory);
        this.jsonLogic = Objects.requireNonNull(jsonLogic);
        this.metrics = Objects.requireNonNull(metrics);

        jsonLogicNodesCache = cacheTtl != null && cacheSize != null
                ? SettingsCache.createCache(cacheTtl, cacheSize)
                : null;
    }

    @Override
    public PrivacyModuleQualifier qualifier() {
        return PrivacyModuleQualifier.US_CUSTOM_LOGIC;
    }

    @Override
    public PrivacyModule from(PrivacyModuleCreationContext creationContext) {
        final AccountUSCustomLogicModuleConfig moduleConfig = moduleConfig(creationContext);
        final GppContext.Scope scope = creationContext.getGppContext().scope();

        final ObjectNode jsonLogicConfig = jsonLogicConfig(moduleConfig, creationContext.getActivity());
        final boolean normalizeSection = normalizeSection(moduleConfig);

        final List<PrivacyModule> innerPrivacyModules = jsonLogicConfig != null
                ? SetUtils.emptyIfNull(scope.getSectionsIds()).stream()
                .filter(sectionId -> shouldApplyPrivacy(sectionId, moduleConfig))
                .map(sectionId -> forConfig(sectionId, normalizeSection, scope.getGppModel(), jsonLogicConfig))
                .toList()
                : Collections.emptyList();

        final AndRule andRule = new AndRule(innerPrivacyModules);
        return andRule::proceed;
    }

    private static AccountUSCustomLogicModuleConfig moduleConfig(PrivacyModuleCreationContext creationContext) {
        return (AccountUSCustomLogicModuleConfig) creationContext.getPrivacyModuleConfig();
    }

    private static ObjectNode jsonLogicConfig(AccountUSCustomLogicModuleConfig moduleConfig, Activity activity) {
        return Stream.ofNullable(moduleConfig.getConfig())
                .map(AccountUSCustomLogicModuleConfig.Config::getActivitiesConfigs)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(activityConfig -> containsActivity(activityConfig, activity))
                .map(AccountUSCustomLogicModuleConfig.ActivityConfig::getJsonLogicNode)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static boolean containsActivity(AccountUSCustomLogicModuleConfig.ActivityConfig activityConfig,
                                            Activity activity) {

        return Optional.ofNullable(activityConfig)
                .map(AccountUSCustomLogicModuleConfig.ActivityConfig::getActivities)
                .orElseGet(Collections::emptySet)
                .contains(activity);
    }

    private static boolean normalizeSection(AccountUSCustomLogicModuleConfig moduleConfig) {
        return Optional.ofNullable(moduleConfig.getConfig())
                .map(AccountUSCustomLogicModuleConfig.Config::getNormalizeSections)
                .orElse(true);
    }

    private static boolean shouldApplyPrivacy(Integer sectionId, AccountUSCustomLogicModuleConfig moduleConfig) {
        final Set<Integer> sectionIds = moduleConfig.getConfig().getSids();
        return ALLOWED_SECTIONS_IDS.contains(sectionId) && sectionIds != null && sectionIds.contains(sectionId);
    }

    private PrivacyModule forConfig(int sectionId,
                                    boolean normalizeSection,
                                    GppModel gppModel,
                                    ObjectNode jsonLogicConfig) {

        return new USCustomLogicModule(
                jsonLogic,
                jsonLogicNode(jsonLogicConfig),
                USCustomLogicDataSupplier.of(gppReaderFactory.forSection(sectionId, normalizeSection, gppModel)));
    }

    private JsonLogicNode jsonLogicNode(ObjectNode jsonLogicConfig) {
        final String jsonAsString = jsonLogicConfig.toString();
        return jsonLogicNodesCache != null
                ? jsonLogicNodesCache.computeIfAbsent(jsonAsString, this::parseJsonLogicNode)
                : parseJsonLogicNode(jsonAsString);
    }

    private JsonLogicNode parseJsonLogicNode(String jsonLogicConfig) {
        try {
            metrics.updateAlertsMetrics(MetricName.general);
            return jsonLogic.parse(jsonLogicConfig);
        } catch (DecodeException e) {
            throw new PreBidException("JsonLogic exception: " + e.getMessage());
        }
    }
}
