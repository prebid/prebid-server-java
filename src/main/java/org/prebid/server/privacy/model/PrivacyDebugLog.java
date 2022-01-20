package org.prebid.server.privacy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iabtcf.decoder.TCString;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.util.ObjectUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Value
@Builder(toBuilder = true)
public class PrivacyDebugLog {

    PrivacyDebug originPrivacy;

    PrivacyDebug resolvedPrivacy;

    Map<String, Set<String>> privacyActionsPerBidder;

    public static PrivacyDebugLog from(Privacy originPrivacy, Privacy resolvedPrivacy, TcfContext tcfContext) {
        return PrivacyDebugLog.builder()
                .originPrivacy(makeOriginPrivacyDebug(originPrivacy))
                .resolvedPrivacy(makeResolvedPrivacyDebug(resolvedPrivacy, tcfContext))
                .privacyActionsPerBidder(new HashMap<>())
                .build();
    }

    private static PrivacyDebug makeOriginPrivacyDebug(Privacy privacy) {
        return PrivacyDebug.of(
                PrivacyTcfDebug.of(privacy.getGdpr(), privacy.getConsentString()),
                PrivacyCcpaDebug.of(extractUsPrivacy(privacy)),
                PrivacyCoppaDebug.of(privacy.getCoppa()));
    }

    private static PrivacyDebug makeResolvedPrivacyDebug(Privacy privacy, TcfContext tcfContext) {
        final PrivacyTcfDebug privacyTcfDebug = PrivacyTcfDebug.of(
                ObjectUtil.getIfNotNull(tcfContext, TcfContext::getGdpr),
                ObjectUtil.getIfNotNull(tcfContext, TcfContext::getConsentString),
                extractTcfVersion(tcfContext),
                ObjectUtil.getIfNotNull(tcfContext, TcfContext::getInEea));

        final PrivacyCcpaDebug privacyCcpaDebug = PrivacyCcpaDebug.of(extractUsPrivacy(privacy));

        final PrivacyCoppaDebug privacyCoppaDebug = PrivacyCoppaDebug.of(
                ObjectUtil.getIfNotNull(privacy, Privacy::getCoppa));

        return PrivacyDebug.of(privacyTcfDebug, privacyCcpaDebug, privacyCoppaDebug);
    }

    private static Integer extractTcfVersion(TcfContext tcfContext) {
        final TCString tcString = ObjectUtil.getIfNotNull(tcfContext, TcfContext::getConsent);
        return ObjectUtil.getIfNotNull(tcString, TCString::getVersion);
    }

    private static String extractUsPrivacy(Privacy privacy) {
        final Ccpa ccpa = ObjectUtil.getIfNotNull(privacy, Privacy::getCcpa);
        return ObjectUtil.getIfNotNull(ccpa, Ccpa::getUsPrivacy);
    }

    public void addBidderLog(String bidder, Set<String> logs) {
        if (CollectionUtils.isNotEmpty(logs) && StringUtils.isNotBlank(bidder)) {
            privacyActionsPerBidder.put(bidder, logs);
        }
    }

    @Value(staticConstructor = "of")
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private static class PrivacyDebug {

        PrivacyTcfDebug tcf;

        PrivacyCcpaDebug ccpa;

        PrivacyCoppaDebug coppa;
    }

    @Value(staticConstructor = "of")
    private static class PrivacyTcfDebug {

        String gdpr;

        String tcfConsentString;

        Integer tcfConsentVersion;

        Boolean inEea;

        public static PrivacyTcfDebug of(String gdpr, String tcfConsentString) {
            return of(gdpr, tcfConsentString, null, null);
        }
    }

    @Value(staticConstructor = "of")
    private static class PrivacyCcpaDebug {

        String usPrivacy;
    }

    @Value(staticConstructor = "of")
    private static class PrivacyCoppaDebug {

        Integer coppa;
    }
}
