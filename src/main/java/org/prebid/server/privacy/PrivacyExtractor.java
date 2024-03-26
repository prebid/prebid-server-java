package org.prebid.server.privacy;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.http.HttpServerRequest;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.request.CookieSyncRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GDPR-aware utilities
 */
public class PrivacyExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyExtractor.class);

    private static final String SETUID_GDPR_PARAM = "gdpr";
    private static final String SETUID_GDPR_CONSENT_PARAM = "gdpr_consent";
    private static final String SETUID_GPP_PARAM = "gpp";
    private static final String SETUID_GPP_SID_PARAM = "gpp_sid";

    private static final String DEFAULT_CONSENT_VALUE = "";
    private static final String DEFAULT_GDPR_VALUE = "";
    private static final Ccpa DEFAULT_CCPA_VALUE = Ccpa.EMPTY;
    private static final Integer DEFAULT_COPPA_VALUE = 0;
    private static final String DEFAULT_GPP_VALUE = "";
    private static final List<Integer> DEFAULT_GPP_SID_VALUE = Collections.emptyList();

    /**
     * Retrieves:
     * <p><ul>
     * <li>gdpr from regs.ext.gdpr
     * <li>consent from user.consent
     * <li>us_privacy from regs.ext.us_privacy
     * <li>coppa from regs.coppa
     * </ul><p>
     * And construct {@link Privacy} from them. Use default values in case of invalid value.
     */
    public Privacy validPrivacyFrom(BidRequest bidRequest, List<String> errors) {
        return extractPrivacy(bidRequest.getRegs(), bidRequest.getUser(), errors);
    }

    public Privacy validPrivacyFrom(CookieSyncRequest request) {
        final Integer gdprAsInteger = request.getGdpr();
        final String gdpr = gdprAsInteger != null ? gdprAsInteger.toString() : null;
        final String gdprConsent = request.getGdprConsent();
        final String usPrivacy = request.getUsPrivacy();
        final String gpp = request.getGpp();
        final List<Integer> gppSid = request.getGppSid();

        return toValidPrivacy(gdpr, gdprConsent, usPrivacy, null, gpp, gppSid, null);
    }

    public Privacy validPrivacyFromSetuidRequest(HttpServerRequest request) {
        final String gdpr = request.getParam(SETUID_GDPR_PARAM);
        final String gdprConsent = request.getParam(SETUID_GDPR_CONSENT_PARAM);
        final String gpp = request.getParam(SETUID_GPP_PARAM);
        final List<Integer> gppSid = parseGppSid(request.getParam(SETUID_GPP_SID_PARAM));

        return toValidPrivacy(gdpr, gdprConsent, null, null, gpp, gppSid, null);
    }

    private static List<Integer> parseGppSid(String gppSid) {
        if (gppSid == null) {
            return DEFAULT_GPP_SID_VALUE;
        }

        try {
            return Arrays.stream(gppSid.split(","))
                    .map(StringUtils::strip)
                    .filter(StringUtils::isNotBlank)
                    .map(Integer::parseInt)
                    .toList();
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("invalid %s value. Comma separated integers expected."
                    .formatted(SETUID_GPP_SID_PARAM));
        }
    }

    private Privacy extractPrivacy(Regs regs, User user, List<String> errors) {
        final Integer extRegsGdpr = regs != null ? regs.getGdpr() : null;
        final String gdpr = extRegsGdpr != null ? Integer.toString(extRegsGdpr) : null;
        final String consent = user != null ? user.getConsent() : null;
        final String usPrivacy = regs != null ? regs.getUsPrivacy() : null;
        final Integer coppa = regs != null ? regs.getCoppa() : null;

        return toValidPrivacy(gdpr, consent, usPrivacy, coppa, null, null, errors);
    }

    public Privacy toValidPrivacy(String gdpr,
                                  String consent,
                                  String usPrivacy,
                                  Integer coppa,
                                  String gpp,
                                  List<Integer> gppSid,
                                  List<String> errors) {

        final String validGdpr = ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0")
                ? DEFAULT_GDPR_VALUE
                : gdpr;
        final String validConsent = StringUtils.defaultString(consent, DEFAULT_CONSENT_VALUE);
        final Ccpa validCcpa = usPrivacy == null ? DEFAULT_CCPA_VALUE : toValidCcpa(usPrivacy, errors);
        final Integer validCoppa = coppa == null ? DEFAULT_COPPA_VALUE : coppa;
        final String validGpp = StringUtils.defaultString(gpp, DEFAULT_GPP_VALUE);
        final List<Integer> validGppSid = ListUtils.defaultIfNull(gppSid, DEFAULT_GPP_SID_VALUE);

        return Privacy.builder()
                .gdpr(validGdpr)
                .consentString(validConsent)
                .ccpa(validCcpa)
                .coppa(validCoppa)
                .gpp(validGpp)
                .gppSid(validGppSid)
                .build();
    }

    private static Ccpa toValidCcpa(String usPrivacy, List<String> errors) {
        try {
            Ccpa.validateUsPrivacy(usPrivacy);
            return Ccpa.of(usPrivacy);
        } catch (PreBidException e) {
            final String message = "CCPA consent %s has invalid format: %s".formatted(usPrivacy, e.getMessage());
            logger.debug(message);
            if (errors != null) {
                errors.add(message);
            }
            return DEFAULT_CCPA_VALUE;
        }
    }
}
