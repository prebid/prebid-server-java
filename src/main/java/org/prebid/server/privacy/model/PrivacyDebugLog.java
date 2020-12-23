package org.prebid.server.privacy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iabtcf.decoder.TCString;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class PrivacyDebugLog {

    PrivacyDebug originPrivacy;

    PrivacyDebug resolvedPrivacy;

    Map<String, Set<String>> privacyActionsPerBidder;

    List<String> errors;

    public static PrivacyDebugLog from(Privacy originPrivacy,
                                       Privacy resolvedPrivacy,
                                       TcfContext tcfContext,
                                       List<String> errors) {
        return PrivacyDebugLog.builder()
                .originPrivacy(makeOriginPrivacyDebug(originPrivacy))
                .resolvedPrivacy(makeResolvedPrivacyDebug(resolvedPrivacy, tcfContext))
                .errors(errors)
                .privacyActionsPerBidder(new HashMap<>())
                .build();
    }

    private static PrivacyDebug makeOriginPrivacyDebug(Privacy privacy) {
        return PrivacyDebug.of(
                PrivacyTcfDebug.of(privacy.getGdpr(), privacy.getConsentString(), null, null),
                PrivacyCcpaDebug.of(extractUsPrivacy(privacy.getCcpa())),
                PrivacyCoppaDebug.of(privacy.getCoppa()));
    }

    private static PrivacyDebug makeResolvedPrivacyDebug(Privacy privacy, TcfContext tcfContext) {
        return PrivacyDebug.of(
                PrivacyTcfDebug.of(
                        tcfContext != null ? tcfContext.getGdpr() : null,
                        tcfContext != null ? tcfContext.getConsentString() : null,
                        tcfContext != null ? extractTcfVersion(tcfContext.getConsent()) : null,
                        tcfContext != null ? tcfContext.getInEea() : null),
                PrivacyCcpaDebug.of(extractUsPrivacy(privacy != null ? privacy.getCcpa() : null)),
                PrivacyCoppaDebug.of(privacy != null ? privacy.getCoppa() : null)
        );
    }

    private static Integer extractTcfVersion(TCString tcString) {
        return tcString != null ? tcString.getVersion() : null;
    }

    private static String extractUsPrivacy(Ccpa ccpa) {
        return ccpa != null ? ccpa.getUsPrivacy() : null;
    }

    public void addBidderLog(String bidder, Set<String> logs) {
        if (CollectionUtils.isNotEmpty(logs)) {
            privacyActionsPerBidder.put(bidder, logs);
        }
    }

    @Value(staticConstructor = "of")
    private static class PrivacyDebug {

        PrivacyTcfDebug tcf;

        PrivacyCcpaDebug ccpa;

        PrivacyCoppaDebug coppa;
    }

    @Value(staticConstructor = "of")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private static class PrivacyTcfDebug {

        String gdpr;

        String tcfConsentString;

        Integer tcfConsentVersion;

        Boolean inEea;
    }

    @Value(staticConstructor = "of")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private static class PrivacyCcpaDebug {

        String usPrivacy;
    }

    @Value(staticConstructor = "of")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private static class PrivacyCoppaDebug {
        Integer coppa;
    }

}
