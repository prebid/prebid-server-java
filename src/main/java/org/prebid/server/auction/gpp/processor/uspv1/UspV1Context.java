package org.prebid.server.auction.gpp.processor.uspv1;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.UspV1;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class UspV1Context {

    private final GppModel gppModel;

    private final Set<Integer> sectionsIds;

    private final List<String> errors = new ArrayList<>();

    UspV1Context(GppModel gppModel, Set<Integer> sectionsIds) {
        this.gppModel = gppModel;
        this.sectionsIds = sectionsIds;
    }

    public static UspV1Context of(GppModel gppModel, Set<Integer> sectionsIds) {
        return new UspV1Context(gppModel, sectionsIds);
    }

    public List<String> getErrors() {
        return errors;
    }

    public UpdateResult<String> resolveUsPrivacy(String usPrivacy) {
        if (!isValidScope()) {
            return UpdateResult.unaltered(usPrivacy);
        }

        if (usPrivacy == null) {
            return UpdateResult.updated(usPrivacyFromGpp());
        }

        try {
            validateExistingUsPrivacy(usPrivacy);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return UpdateResult.unaltered(usPrivacy);
    }

    private boolean isValidScope() {
        return sectionsIds != null && sectionsIds.contains(UspV1.ID)
                && gppModel != null && gppModel.hasSection(UspV1.ID);
    }

    private String usPrivacyFromGpp() {
        try {
            return gppModel.encodeSection(UspV1.ID);
        } catch (EncodingException e) {
            return null;
        }
    }

    private void validateExistingUsPrivacy(String usPrivacy) {
        if (!usPrivacy.equals(usPrivacyFromGpp())) {
            throw new PreBidException("USP string does not match regs.us_privacy");
        }
    }
}
