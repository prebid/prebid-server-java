package org.prebid.server.auction.gpp.processor.tcfeuv2;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.TcfEuV2;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.UpdateResult;

import java.util.List;
import java.util.Set;

public class TcfEuV2ContextProcessor implements GppContextProcessor {

    @Override
    public GppContext process(GppContext gppContext) {
        final GppContext.Scope scope = gppContext.scope();
        final Set<Integer> sectionsIds = scope.getSectionsIds();

        final GppContext.Regions regions = gppContext.regions();
        final TcfEuV2Privacy tcfEuV2Privacy = regions.getTcfEuV2Privacy();

        final List<String> errors = gppContext.errors();

        final UpdateResult<Integer> resolvedGdpr = resolveGdpr(tcfEuV2Privacy.getGdpr(), sectionsIds, errors);
        final UpdateResult<String> resolvedConsent = resolveConsent(
                tcfEuV2Privacy.getConsent(), scope.getGppModel(), sectionsIds, errors);

        return resolvedGdpr.isUpdated() || resolvedConsent.isUpdated()
                ? gppContext.with(TcfEuV2Privacy.of(resolvedGdpr.getValue(), resolvedConsent.getValue()))
                : gppContext;
    }

    public UpdateResult<Integer> resolveGdpr(Integer gdpr, Set<Integer> sectionsIds, List<String> errors) {
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

    private Integer gdprFromGppSid(Set<Integer> sectionsIds) {
        return sectionsIds.contains(TcfEuV2.ID) ? 1 : 0;
    }

    private void validateExistingGdpr(Integer gdpr, Set<Integer> sectionsIds) {
        if (isNotInTcfEuV2Scope(gdpr, sectionsIds)) {
            throw new PreBidException("GPP scope does not match TCF2 scope");
        }
    }

    private boolean isNotInTcfEuV2Scope(Integer gdpr, Set<Integer> sectionsIds) {
        final boolean containsTcfEuV2Section = sectionsIds.contains(TcfEuV2.ID);
        return (containsTcfEuV2Section && gdpr != 1) || (!containsTcfEuV2Section && gdpr != 0);
    }

    public UpdateResult<String> resolveConsent(String consent,
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

    private boolean isValidScope(GppModel gppModel, Set<Integer> sectionsIds) {
        return sectionsIds != null && sectionsIds.contains(TcfEuV2.ID)
                && gppModel != null && gppModel.hasSection(TcfEuV2.ID);
    }

    private String consentFromGpp(GppModel gppModel) {
        try {
            return gppModel.encodeSection(TcfEuV2.ID);
        } catch (EncodingException e) {
            return null;
        }
    }

    private void validateExistingConsent(String consent, GppModel gppModel) {
        if (!consent.equals(consentFromGpp(gppModel))) {
            throw new PreBidException("GPP TCF2 string does not match user.consent");
        }
    }
}
