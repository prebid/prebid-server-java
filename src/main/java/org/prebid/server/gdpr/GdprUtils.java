package org.prebid.server.gdpr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

/**
 * GDPR-aware utilities
 */
public class GdprUtils {

    private GdprUtils() {
    }

    /**
     * Retrieves gdpr from regs.ext.gdpr and in case of any exception or invalid values returns empty string.
     */
    public static String gdprFrom(Regs regs) {
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
    public static String gdprConsentFrom(User user) {
        final ObjectNode extUserNode = user != null ? user.getExt() : null;
        ExtUser extUser;
        try {
            extUser = extUserNode != null ? Json.mapper.treeToValue(extUserNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            extUser = null;
        }

        final String gdprConsent = extUser != null ? extUser.getConsent() : "";
        return ObjectUtils.firstNonNull(gdprConsent, "");
    }
}
