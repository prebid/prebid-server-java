package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import com.iab.gpp.encoder.GppModel;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatModule;
import org.prebid.server.activity.infrastructure.rule.AndRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class USNatModuleCreator implements PrivacyModuleCreator {

    private static final Set<Integer> ALLOWED_SECTIONS_IDS =
            Arrays.stream(USNatSection.values())
                    .map(USNatSection::sectionId)
                    .collect(Collectors.toSet());

    private final USNatGppReaderFactory gppReaderFactory;

    public USNatModuleCreator(USNatGppReaderFactory gppReaderFactory) {
        this.gppReaderFactory = Objects.requireNonNull(gppReaderFactory);
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
                .map(sectionId -> forSection(creationContext.getActivity(), sectionId, scope.getGppModel()))
                .toList();

        final AndRule andRule = new AndRule(innerPrivacyModules);
        return andRule::proceed;
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

    private PrivacyModule forSection(Activity activity, Integer sectionId, GppModel gppModel) {
        return new USNatModule(activity, gppReaderFactory.forSection(sectionId, gppModel));
    }
}
