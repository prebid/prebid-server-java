package org.prebid.server.bidder;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.Uri;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class UsersyncInfoFactory {

    private static final String CALLBACK_RELATIVE_URL_TEMPLATE = """
            /setuid?\
            bidder={bidder}\
            &gdpr={gdpr}\
            &gdpr_consent={gdpr_consent}\
            &us_privacy={us_privacy}\
            &gpp={gpp}\
            &gpp_sid={gpp_sid}\
            &f={format}""";

    private static final String BIDDER_PLACEHOLDER = "bidder";
    private static final String GDPR_PLACEHOLDER = "gdpr";
    private static final String GDPR_CONSENT_PLACEHOLDER = "gdpr_consent";
    private static final String US_PRIVACY_PLACEHOLDER = "us_privacy";
    private static final String GPP_PLACEHOLDER = "gpp";
    private static final String GPP_SID_PLACEHOLDER = "gpp_sid";
    private static final String REDIRECT_URL_PLACEHOLDER = "redirect_url";
    private static final String FORMAT_PLACEHOLDER = "format";

    private final Uri callbackUrlTemplate;

    public UsersyncInfoFactory(String externalUrl) {
        this.callbackUrlTemplate = Uri.of(Objects.requireNonNull(externalUrl) + CALLBACK_RELATIVE_URL_TEMPLATE);
    }

    public UsersyncInfo build(String bidder,
                              String hostCookieUid,
                              UsersyncMethod usersyncMethod,
                              Privacy privacy) {

        return UsersyncInfo.of(
                hostCookieUid == null
                        ? buildSyncUrl(bidder, usersyncMethod, privacy)
                        : buildSetUidUrl(bidder, HttpUtil.encodeUrl(hostCookieUid), usersyncMethod, privacy),
                usersyncMethod.getType());
    }

    private String buildSyncUrl(String bidder, UsersyncMethod usersyncMethod, Privacy privacy) {
        return usersyncMethod.getUsersyncUrl().parameterized()
                .replaceMacros(preparePrivacyParams(privacy))
                .replaceMacro(
                        REDIRECT_URL_PLACEHOLDER,
                        buildSetUidUrl(bidder, usersyncMethod.getUidMacro(), usersyncMethod, privacy))
                .expand();
    }

    private String buildSetUidUrl(String bidder, String uid, UsersyncMethod usersyncMethod, Privacy privacy) {
        return callbackUrlTemplate
                .replaceMacros(prepareRedirectParams(bidder, uid, usersyncMethod))
                .replaceMacros(preparePrivacyParams(privacy))
                .expand()
                + "&uid=" + StringUtils.defaultString(uid); // uid macro should not be url-encoded
    }

    private Map<String, String> prepareRedirectParams(String bidder, String uid, UsersyncMethod method) {
        return Map.of(
                BIDDER_PLACEHOLDER, bidder,
                FORMAT_PLACEHOLDER, resolveFormat(method).name);
    }

    private static UsersyncFormat resolveFormat(UsersyncMethod method) {
        return ObjectUtils.firstNonNull(method.getFormatOverride(), method.getType().format);
    }

    private static Map<String, String> preparePrivacyParams(Privacy privacy) {
        return Map.of(
                GDPR_PLACEHOLDER, StringUtils.defaultString(privacy.getGdpr()),
                GDPR_CONSENT_PLACEHOLDER, StringUtils.defaultString(privacy.getConsentString()),
                US_PRIVACY_PLACEHOLDER, StringUtils.defaultString(privacy.getCcpa().getUsPrivacy()),
                GPP_PLACEHOLDER, StringUtils.defaultString(privacy.getGpp()),
                GPP_SID_PLACEHOLDER, CollectionUtils.emptyIfNull(privacy.getGppSid()).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")));
    }
}
