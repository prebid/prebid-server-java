package org.prebid.server.bidder.adform;

import com.iab.openrtb.request.Regs;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

/**
 * Util class to help {@link org.prebid.server.bidder.adform.AdformBidder} and
 * {@link org.prebid.server.bidder.adform.AdformAdapter} to retrieve data from request.
 */
class AdformRequestUtil {

    /**
     * Retrieves gdpr from regs.ext.gdpr and in case of any exception or invalid values returns empty string.
     */
    String getGdprApplies(Regs regs) {
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final String gdpr = extRegs != null ? Integer.toString(extRegs.getGdpr()) : "";

        return ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0") ? "" : gdpr;
    }

    /**
     * Retrieves consent from user.ext.consent and in case of any exception or invalid values return empty string.
     */
    String getConsent(ExtUser extUser) {
        final String gdprConsent = extUser != null ? extUser.getConsent() : "";
        return ObjectUtils.defaultIfNull(gdprConsent, "");
    }
}
