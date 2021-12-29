package org.prebid.server.privacy;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.PrebidLog;
import org.prebid.server.auction.model.PrivacyMessageFactory;
import org.prebid.server.auction.model.PrivacyMessageType;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.CookieSyncRequest;

/**
 * GDPR-aware utilities
 */
public class PrivacyExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyExtractor.class);

    private static final String SETUID_GDPR_PARAM = "gdpr";
    private static final String SETUID_GDPR_CONSENT_PARAM = "gdpr_consent";

    private static final String DEFAULT_CONSENT_VALUE = "";
    private static final String DEFAULT_GDPR_VALUE = "";
    private static final Ccpa DEFAULT_CCPA_VALUE = Ccpa.EMPTY;
    private static final Integer DEFAULT_COPPA_VALUE = 0;

    /**
     * Retrieves:
     * <p><ul>
     * <li>gdpr from regs.ext.gdpr
     * <li>consent from user.ext.consent
     * <li>us_privacy from regs.ext.us_privacy
     * <li>coppa from regs.coppa
     * </ul><p>
     * And construct {@link Privacy} from them. Use default values in case of invalid value.
     */
    public Privacy validPrivacyFrom(BidRequest bidRequest, PrebidLog prebidLog) {
        return extractPrivacy(bidRequest.getRegs(), bidRequest.getUser(), prebidLog);
    }

    public Privacy validPrivacyFrom(CookieSyncRequest request) {
        final Integer gdprAsInteger = request.getGdpr();
        final String gdpr = gdprAsInteger != null ? gdprAsInteger.toString() : null;
        final String gdprConsent = request.getGdprConsent();
        final String usPrivacy = request.getUsPrivacy();

        return toValidPrivacy(gdpr, gdprConsent, usPrivacy, null, null);
    }

    public Privacy validPrivacyFromSetuidRequest(HttpServerRequest request) {
        final String gdpr = request.getParam(SETUID_GDPR_PARAM);
        final String gdprConsent = request.getParam(SETUID_GDPR_CONSENT_PARAM);

        return toValidPrivacy(gdpr, gdprConsent, null, null, null);
    }

    private Privacy extractPrivacy(Regs regs, User user, PrebidLog prebidLog) {
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final ExtUser extUser = user != null ? user.getExt() : null;

        final Integer extRegsGdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdpr = extRegsGdpr != null ? Integer.toString(extRegsGdpr) : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        final String usPrivacy = extRegs != null ? extRegs.getUsPrivacy() : null;
        final Integer coppa = regs != null ? regs.getCoppa() : null;

        return toValidPrivacy(gdpr, consent, usPrivacy, coppa, prebidLog);
    }

    private static Privacy toValidPrivacy(String gdpr,
                                          String consent,
                                          String usPrivacy,
                                          Integer coppa,
                                          PrebidLog prebidLog) {
        final String validGdpr = ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0")
                ? DEFAULT_GDPR_VALUE
                : gdpr;
        final String validConsent = consent == null ? DEFAULT_CONSENT_VALUE : consent;
        final Ccpa validCcpa = usPrivacy == null ? DEFAULT_CCPA_VALUE : toValidCcpa(usPrivacy, prebidLog);
        final Integer validCoppa = coppa == null ? DEFAULT_COPPA_VALUE : coppa;

        return Privacy.of(validGdpr, validConsent, validCcpa, validCoppa);
    }

    private static Ccpa toValidCcpa(String usPrivacy, PrebidLog prebidLog) {
        try {
            Ccpa.validateUsPrivacy(usPrivacy);
            return Ccpa.of(usPrivacy);
        } catch (PreBidException e) {
            final String message = String.format("CCPA consent %s has invalid format: %s", usPrivacy, e.getMessage());
            logger.debug(message);
            if (prebidLog != null) {
                prebidLog.logMessage(PrivacyMessageFactory.error(PrivacyMessageType.ccpa_error, message));
            }
            return DEFAULT_CCPA_VALUE;
        }
    }
}
