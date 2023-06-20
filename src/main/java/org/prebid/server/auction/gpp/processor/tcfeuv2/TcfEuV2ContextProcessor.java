package org.prebid.server.auction.gpp.processor.tcfeuv2;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.TcfEuV2;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TcfEuV2ContextProcessor implements GppContextProcessor {

    @Override
    public GppContextWrapper process(GppContext gppContext) {
        final List<String> errors = new ArrayList<>();
        return GppContextWrapper.of(process(gppContext, errors), errors);
    }

    private static GppContext process(GppContext gppContext, List<String> errors) {
        final GppContext.Scope scope = gppContext.scope();
        final Set<Integer> sectionsIds = scope.getSectionsIds();
        final TcfEuV2Privacy tcfEuV2Privacy = gppContext.regions().getTcfEuV2Privacy();

        final UpdateResult<Integer> resolvedGdpr = resolveGdpr(
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getGdpr() : null, sectionsIds, errors);
        final UpdateResult<String> resolvedConsent = resolveConsent(
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getConsent() : null, scope.getGppModel(), sectionsIds, errors);

        return resolvedGdpr.isUpdated() || resolvedConsent.isUpdated()
                ? gppContext.with(TcfEuV2Privacy.of(resolvedGdpr.getValue(), resolvedConsent.getValue()))
                : gppContext;
    }

    private static UpdateResult<Integer> resolveGdpr(Integer gdpr, Set<Integer> sectionsIds, List<String> errors) {
        if (sectionsIds == null) {
            return UpdateResult.unaltered(gdpr);
        }

        if (gdpr == null) {
            return UpdateResult.updated(gdprFromGppSid(sectionsIds));
        }

        try {
            validateExistingGdpr(gdpr, sectionsIds);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return UpdateResult.unaltered(gdpr);
    }

    private static Integer gdprFromGppSid(Set<Integer> sectionsIds) {
        return sectionsIds.contains(TcfEuV2.ID) ? 1 : 0;
    }

    private static void validateExistingGdpr(Integer gdpr, Set<Integer> sectionsIds) {
        if (isNotInTcfEuV2Scope(gdpr, sectionsIds)) {
            throw new PreBidException("GPP scope does not match TCF2 scope");
        }
    }

    private static boolean isNotInTcfEuV2Scope(Integer gdpr, Set<Integer> sectionsIds) {
        final boolean containsTcfEuV2Section = sectionsIds.contains(TcfEuV2.ID);
        return (containsTcfEuV2Section && gdpr != 1) || (!containsTcfEuV2Section && gdpr != 0);
    }

    private static UpdateResult<String> resolveConsent(String consent,
                                                       GppModel gppModel,
                                                       Set<Integer> sectionsIds,
                                                       List<String> errors) {

        if (!isValidScope(gppModel, sectionsIds)) {
            return UpdateResult.unaltered(consent);
        }

        if (consent == null) {
            return UpdateResult.updated(consentFromGpp(gppModel));
        }

        try {
            validateExistingConsent(consent, gppModel);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return UpdateResult.unaltered(consent);
    }

    private static boolean isValidScope(GppModel gppModel, Set<Integer> sectionsIds) {
        return sectionsIds != null && sectionsIds.contains(TcfEuV2.ID)
                && gppModel != null && gppModel.hasSection(TcfEuV2.ID);
    }

    private static String consentFromGpp(GppModel gppModel) {
        try {
            return gppModel.encodeSection(TcfEuV2.ID);
        } catch (EncodingException e) {
            return null;
        }
    }

    private static void validateExistingConsent(String consent, GppModel gppModel) {
        if (!consent.equals(consentFromGpp(gppModel))) {
            throw new PreBidException("GPP TCF2 string does not match user.consent");
        }
    }
}
