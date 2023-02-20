package org.prebid.server.auction.gpp.processor.tcfeuv2;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.TcfEuV2;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class TcfEuV2Context {

    private final GppModel gppModel;

    private final Set<Integer> sectionsIds;

    private final List<String> errors = new ArrayList<>();

    TcfEuV2Context(GppModel gppModel, Set<Integer> sectionsIds) {
        this.gppModel = gppModel;
        this.sectionsIds = sectionsIds;
    }

    public static TcfEuV2Context of(GppModel gppModel, Set<Integer> sectionsIds) {
        return new TcfEuV2Context(gppModel, sectionsIds);
    }

    public List<String> getErrors() {
        return errors;
    }

    public UpdateResult<Integer> resolveGdpr(Integer gdpr) {
        if (sectionsIds == null) {
            return UpdateResult.unaltered(gdpr);
        }

        if (gdpr == null) {
            return UpdateResult.updated(gdprFromGppSid());
        }

        try {
            validateExistingGdpr(gdpr);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return UpdateResult.unaltered(gdpr);
    }

    private Integer gdprFromGppSid() {
        return sectionsIds.contains(TcfEuV2.ID) ? 1 : 0;
    }

    private void validateExistingGdpr(Integer gdpr) {
        if (isNotInTcfEuV2Scope(gdpr)) {
            throw new PreBidException("GPP scope does not match TCF2 scope");
        }
    }

    private boolean isNotInTcfEuV2Scope(Integer gdpr) {
        final boolean containsTcfEuV2Section = sectionsIds.contains(TcfEuV2.ID);
        return (containsTcfEuV2Section && gdpr != 1) || (!containsTcfEuV2Section && gdpr != 0);
    }

    public UpdateResult<String> resolveConsent(String consent) {
        if (!isValidScope()) {
            return UpdateResult.unaltered(consent);
        }

        if (consent == null) {
            return UpdateResult.updated(consentFromGpp());
        }

        try {
            validateExistingConsent(consent);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return UpdateResult.unaltered(consent);
    }

    private boolean isValidScope() {
        return sectionsIds != null && sectionsIds.contains(TcfEuV2.ID)
                && gppModel != null && gppModel.hasSection(TcfEuV2.ID);
    }

    private String consentFromGpp() {
        try {
            return gppModel.encodeSection(TcfEuV2.ID);
        } catch (EncodingException e) {
            return null;
        }
    }

    private void validateExistingConsent(String consent) {
        if (!consent.equals(consentFromGpp())) {
            throw new PreBidException("GPP TCF2 string does not match user.consent");
        }
    }
}
