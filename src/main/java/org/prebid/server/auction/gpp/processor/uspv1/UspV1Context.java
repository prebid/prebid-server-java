package org.prebid.server.auction.gpp.processor.uspv1;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.UspV1;
import lombok.Value;
import org.prebid.server.exception.PreBidException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Value(staticConstructor = "of")
class UspV1Context {

    GppModel gppModel;

    Set<Integer> sectionsIds;

    List<String> errors = new ArrayList<>();

    public String resolveUsPrivacy(String usPrivacy) {
        if (!isValidScope()) {
            return null;
        }

        if (usPrivacy == null) {
            return usPrivacyFromGpp();
        }

        try {
            validateExistingUsPrivacy(usPrivacy);
        } catch (PreBidException e) {
            errors.add(e.getMessage());
        }

        return null;
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
