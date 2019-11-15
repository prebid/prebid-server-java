package org.prebid.server.privacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.json.Json;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

/**
 * GDPR-aware utilities
 */
public class PrivacyExtractor {

    private PrivacyExtractor() {
    }

    /**
     * Retrieves:
     * <p><ul>
     * <li>gdpr from regs.ext.gdpr
     * <li>us_privacy from regs.ext.us_privacy
     * <li>consent from user.ext.consent
     * </ul><p>
     * And construct {@link Privacy} from them
     */
    public static Privacy privacyFrom(Regs regs, User user) {
        final ExtRegs extRegs = extRegs(regs);
        final ExtUser extUser = extUser(user);

        final String gdpr = extRegs != null ? Integer.toString(extRegs.getGdpr()) : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        final String ccpa = extRegs != null ? extRegs.getUsPrivacy() : null;

        return Privacy.of(gdpr, consent, ccpa);
    }

    private static ExtRegs extRegs(Regs regs) {
        final ObjectNode extRegsNode = regs != null ? regs.getExt() : null;
        try {
            return extRegsNode != null ? Json.mapper.treeToValue(extRegsNode, ExtRegs.class) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static ExtUser extUser(User user) {
        final ObjectNode extUserNode = user != null ? user.getExt() : null;
        try {
            return extUserNode != null ? Json.mapper.treeToValue(extUserNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

