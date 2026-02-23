package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import com.iab.gpp.encoder.GppModel;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.privacy.AndPrivacyModules;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.PrivacySection;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatModule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class USNatModuleCreator implements PrivacyModuleCreator {

    private static final Logger logger = LoggerFactory.getLogger(USNatModuleCreator.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final Set<Integer> ALLOWED_SECTIONS_IDS =
            PrivacySection.US_PRIVACY_SECTIONS.stream()
                    .map(PrivacySection::sectionId)
                    .collect(Collectors.toSet());

    private final USNatGppReaderFactory gppReaderFactory;
    private final Metrics metrics;
    private final double samplingRate;

    public USNatModuleCreator(USNatGppReaderFactory gppReaderFactory, Metrics metrics, double samplingRate) {
        this.gppReaderFactory = Objects.requireNonNull(gppReaderFactory);
        this.metrics = Objects.requireNonNull(metrics);
        this.samplingRate = samplingRate;
    }

    @Override
    public PrivacyModuleQualifier qualifier() {
        return PrivacyModuleQualifier.US_NAT;
    }

    @Override
    public PrivacyModule from(PrivacyModuleCreationContext creationContext) {
        final AccountUSNatModuleConfig moduleConfig = moduleConfig(creationContext);
        final GppContext.Scope scope = creationContext.getGppContext().scope();

        final List<PrivacyModule> innerPrivacyModules = SetUtils.emptyIfNull(scope.getSectionsIds()).stream()
                .filter(sectionId -> !shouldSkip(sectionId, moduleConfig))
                .map(sectionId -> forSection(
                        creationContext.getActivity(),
                        sectionId,
                        scope.getGppModel(),
                        moduleConfig.getConfig()))
                .filter(Objects::nonNull)
                .toList();

        return new AndPrivacyModules(innerPrivacyModules);
    }

    private static AccountUSNatModuleConfig moduleConfig(PrivacyModuleCreationContext creationContext) {
        return (AccountUSNatModuleConfig) creationContext.getPrivacyModuleConfig();
    }

    private static boolean shouldSkip(Integer sectionId, AccountUSNatModuleConfig moduleConfig) {
        final AccountUSNatModuleConfig.Config config = moduleConfig.getConfig();
        final List<Integer> skipSectionIds = config != null ? config.getSkipSids() : null;

        return !ALLOWED_SECTIONS_IDS.contains(sectionId)
                || (skipSectionIds != null && skipSectionIds.contains(sectionId));
    }

    private PrivacyModule forSection(Activity activity,
                                     Integer sectionId,
                                     GppModel gppModel,
                                     AccountUSNatModuleConfig.Config config) {

        try {
            return new USNatModule(activity, gppReaderFactory.forSection(sectionId, gppModel), config);
        } catch (Exception e) {
            conditionalLogger.error(
                    "UsNat privacy module creation failed: %s. Activity: %s. Section: %s. Gpp: %s.".formatted(
                            e.getMessage(), activity, sectionId, gppModel != null ? gppModel.encode() : null),
                    samplingRate);
            metrics.updateAlertsMetrics(MetricName.general);

            return null;
        }
    }
}
