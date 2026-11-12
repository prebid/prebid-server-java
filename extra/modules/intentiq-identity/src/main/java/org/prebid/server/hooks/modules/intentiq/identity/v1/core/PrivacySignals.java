package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the privacy signals of a bid request (GDPR applicability, TCF consent, US privacy, GPP), from the
 * OpenRTB 2.6 fields first and the 2.5 {@code ext} fields as fallback.
 */
public final class PrivacySignals {

    private static final String NO_SIGNALS = "-";
    private static final int FINGERPRINT_HEX_LENGTH = 16;

    private PrivacySignals() {
    }

    public static String gdpr(BidRequest bidRequest) {
        final Regs regs = bidRequest.getRegs();
        if (regs == null) {
            return null;
        }
        final Integer gdpr = regs.getGdpr() != null ? regs.getGdpr()
                : regs.getExt() != null ? regs.getExt().getGdpr() : null;
        return gdpr != null ? String.valueOf(gdpr) : null;
    }

    public static String usPrivacy(BidRequest bidRequest) {
        final Regs regs = bidRequest.getRegs();
        if (regs == null) {
            return null;
        }
        if (StringUtils.isNotBlank(regs.getUsPrivacy())) {
            return regs.getUsPrivacy();
        }
        return regs.getExt() != null ? regs.getExt().getUsPrivacy() : null;
    }

    public static String gpp(BidRequest bidRequest) {
        return bidRequest.getRegs() != null ? bidRequest.getRegs().getGpp() : null;
    }

    public static String consent(BidRequest bidRequest) {
        final User user = bidRequest.getUser();
        if (user == null) {
            return null;
        }
        if (StringUtils.isNotBlank(user.getConsent())) {
            return user.getConsent();
        }
        return user.getExt() != null ? user.getExt().getConsent() : null;
    }

    /** GDPR applies ({@code regs.gdpr = 1}) and the request carries no TCF consent string. */
    public static boolean isGdprWithoutConsent(BidRequest bidRequest) {
        return "1".equals(gdpr(bidRequest)) && StringUtils.isBlank(consent(bidRequest));
    }

    /**
     * A short, stable digest of all privacy signals of the request. Requests whose signals differ (e.g. a
     * user who changed their consent or opted out) get different fingerprints.
     */
    public static String fingerprint(BidRequest bidRequest) {
        final String signals = Stream.of(gdpr(bidRequest), consent(bidRequest), usPrivacy(bidRequest),
                        gpp(bidRequest))
                .map(StringUtils::defaultString)
                .collect(Collectors.joining("|"));
        if (StringUtils.isBlank(signals.replace("|", ""))) {
            return NO_SIGNALS;
        }
        return HexFormat.of().formatHex(sha256(signals)).substring(0, FINGERPRINT_HEX_LENGTH);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
