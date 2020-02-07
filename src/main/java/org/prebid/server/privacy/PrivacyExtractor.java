package org.prebid.server.privacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Objects;

/**
 * GDPR-aware utilities
 */
public class PrivacyExtractor {

    private static final String DEFAULT_CONSENT_VALUE = "";
    private static final String DEFAULT_GDPR_VALUE = "";
    private static final Ccpa DEFAULT_CCPA_VALUE = Ccpa.EMPTY;

    private final JacksonMapper mapper;

    public PrivacyExtractor(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Retrieves:
     * <p><ul>
     * <li>gdpr from regs.ext.gdpr
     * <li>us_privacy from regs.ext.us_privacy
     * <li>consent from user.ext.consent
     * </ul><p>
     * And construct {@link Privacy} from them. Use default values in case of invalid value.
     */
    public Privacy validPrivacyFrom(Regs regs, User user) {
        final ExtRegs extRegs = extRegs(regs);
        final ExtUser extUser = extUser(user);

        final Integer extRegsGdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdpr = extRegsGdpr != null ? Integer.toString(extRegsGdpr) : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        final String usPrivacy = extRegs != null ? extRegs.getUsPrivacy() : null;

        return toValidPrivacy(gdpr, consent, usPrivacy);
    }

    private ExtRegs extRegs(Regs regs) {
        final ObjectNode extRegsNode = regs != null ? regs.getExt() : null;
        try {
            return extRegsNode != null ? mapper.mapper().treeToValue(extRegsNode, ExtRegs.class) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ExtUser extUser(User user) {
        final ObjectNode extUserNode = user != null ? user.getExt() : null;
        try {
            return extUserNode != null ? mapper.mapper().treeToValue(extUserNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Privacy toValidPrivacy(String gdpr, String consent, String usPrivacy) {
        final String validGdpr = ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0")
                ? DEFAULT_GDPR_VALUE
                : gdpr;
        final String validConsent = consent == null ? DEFAULT_CONSENT_VALUE : consent;
        final Ccpa validCCPA = usPrivacy == null ? DEFAULT_CCPA_VALUE : Ccpa.of(usPrivacy);
        return Privacy.of(validGdpr, validConsent, validCCPA);
    }
}

