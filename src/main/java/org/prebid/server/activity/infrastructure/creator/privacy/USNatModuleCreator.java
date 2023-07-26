package org.prebid.server.activity.infrastructure.creator.privacy;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.section.UspCaV1;
import com.iab.gpp.encoder.section.UspCoV1;
import com.iab.gpp.encoder.section.UspCtV1;
import com.iab.gpp.encoder.section.UspNatV1;
import com.iab.gpp.encoder.section.UspUtV1;
import com.iab.gpp.encoder.section.UspVaV1;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USVirginiaGppReader;
import org.prebid.server.activity.infrastructure.rule.AndRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;

import java.util.List;
import java.util.Set;

public class USNatModuleCreator implements PrivacyModuleCreator {

    private static final Set<Integer> ALLOWED_SECTIONS_IDS = Set.of(
            UspNatV1.ID,
            UspCaV1.ID,
            UspVaV1.ID,
            UspCoV1.ID,
            UspUtV1.ID,
            UspCtV1.ID);

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

    private static PrivacyModule forSection(Activity activity, Integer sectionId, GppModel gppModel) {
        return new USNatModule(activity, forSection(sectionId, gppModel));
    }

    private static USNatGppReader forSection(Integer sectionId, GppModel gppModel) {
        return switch (sectionId) {
            case 7 -> new USNationalGppReader(gppModel);
            case 8 -> new USCaliforniaGppReader(gppModel);
            case 9 -> new USVirginiaGppReader(gppModel);
            case 10 -> new USColoradoGppReader(gppModel);
            case 11 -> new USUtahGppReader(gppModel);
            case 12 -> new USConnecticutGppReader(gppModel);
            default -> throw new IllegalStateException("Unexpected sectionId: " + sectionId);
        };
    }
}
