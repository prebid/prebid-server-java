package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.AdformDigitrustPrivacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;

/**
 * Util class to help {@link org.prebid.server.bidder.adform.AdformBidder} and
 * {@link org.prebid.server.bidder.adform.AdformAdapter} to retrieve data from request.
 */
class AdformRequestUtil {

    private static final int DIGITRUST_VERSION = 1;

    private AdformRequestUtil() {
    }

    /**
     * Retrieves {@link ExtUser} from user.ext.
     */
    static ExtUser getExtUser(User user) {
        final ObjectNode extUserNode = user != null ? user.getExt() : null;
        ExtUser extUser;
        try {
            extUser = extUserNode != null ? Json.mapper.treeToValue(extUserNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            extUser = null;
        }
        return extUser;
    }

    /**
     * Retrieves gdpr from regs.ext.gdpr and in case of any exception or invalid values returns empty string.
     */
    static String getGdprApplies(Regs regs) {
        final ObjectNode extRegsNode = regs != null ? regs.getExt() : null;
        final ExtRegs extRegs;
        try {
            extRegs = extRegsNode != null ? Json.mapper.treeToValue(extRegsNode, ExtRegs.class) : null;
        } catch (JsonProcessingException e) {
            return "";
        }

        final String gdpr = extRegs != null ? Integer.toString(extRegs.getGdpr()) : "";

        return ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0") ? "" : gdpr;
    }

    /**
     * Retrieves consent from user.ext.consent and in case of any exception or invalid values return empty string.
     */
    static String getConsent(ExtUser extUser) {
        final String gdprConsent = extUser != null ? extUser.getConsent() : "";
        return ObjectUtils.firstNonNull(gdprConsent, "");
    }

    /**
     * Creates {@link AdformDigitrust} from user.extUser.digitrust, if something wrong, returns null.
     */
    static AdformDigitrust getAdformDigitrust(ExtUser extUser) {
        final ExtUserDigiTrust extUserDigiTrust = extUser != null ? extUser.getDigitrust() : null;
        return extUserDigiTrust != null
                ? AdformDigitrust.of(
                extUserDigiTrust.getId(),
                DIGITRUST_VERSION,
                extUserDigiTrust.getKeyv(),
                AdformDigitrustPrivacy.of(extUserDigiTrust.getPref() != 0))
                : null;
    }
}
