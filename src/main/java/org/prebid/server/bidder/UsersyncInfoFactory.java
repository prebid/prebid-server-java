package org.prebid.server.bidder;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class UsersyncInfoFactory {

    private static final String CALLBACK_URL_TEMPLATE = """
            {{base_url}}/setuid?\
            bidder={{bidder}}\
            &gdpr={{gdpr}}\
            &gdpr_consent={{gdpr_consent}}\
            &us_privacy={{us_privacy}}\
            &gpp={{gpp}}\
            &gpp_sid={{gpp_sid}}\
            &f={{format}}\
            &uid={{uid}}""";

    private static final String BASE_URL_PLACEHOLDER = "{{base_url}}";
    private static final String BIDDER_PLACEHOLDER = "{{bidder}}";
    private static final String GDPR_PLACEHOLDER = "{{gdpr}}";
    private static final String GDPR_CONSENT_PLACEHOLDER = "{{gdpr_consent}}";
    private static final String US_PRIVACY_PLACEHOLDER = "{{us_privacy}}";
    private static final String GPP_PLACEHOLDER = "{{gpp}}";
    private static final String GPP_SID_PLACEHOLDER = "{{gpp_sid}}";
    private static final String REDIRECT_URL_PLACEHOLDER = "{{redirect_url}}";
    private static final String FORMAT_PLACEHOLDER = "{{format}}";
    private static final String UID_PLACEHOLDER = "{{uid}}";

    private final String externalUrl;

    public UsersyncInfoFactory(String externalUrl) {
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    public UsersyncInfo build(String bidder,
                              String hostCookieUid,
                              UsersyncMethod usersyncMethod,
                              Privacy privacy) {

        final String usersyncUrl = hostCookieUid == null
                ? StringUtils.defaultString(usersyncMethod.getUsersyncUrl())
                : buildRedirectUrl(bidder, hostCookieUid, usersyncMethod);

        final String redirectUrl = hostCookieUid == null
                ? buildRedirectUrl(bidder, usersyncMethod.getUidMacro(), usersyncMethod)
                : StringUtils.EMPTY;

        final Map<String, String> privacyParams = preparePrivacyParams(privacy);
        final String usersyncUrlWithPrivacy = applyParams(usersyncUrl, encodeParams(privacyParams));
        final String redirectUrlWithPrivacy = applyParams(redirectUrl, privacyParams);

        final String finalUrl = usersyncUrlWithPrivacy.replace(
                REDIRECT_URL_PLACEHOLDER, HttpUtil.encodeUrl(redirectUrlWithPrivacy));

        return UsersyncInfo.of(finalUrl, usersyncMethod.getType(), usersyncMethod.isSupportCORS());
    }

    private String buildRedirectUrl(String bidder, String uid, UsersyncMethod method) {
        return CALLBACK_URL_TEMPLATE
                .replace(BASE_URL_PLACEHOLDER, externalUrl)
                .replace(BIDDER_PLACEHOLDER, HttpUtil.encodeUrl(bidder))
                .replace(UID_PLACEHOLDER, StringUtils.defaultString(uid))
                .replace(FORMAT_PLACEHOLDER, resolveFormat(method).name);
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
                        .collect(Collectors.joining(","))
        );
    }

    private static Map<String, String> encodeParams(Map<String, String> params) {
        return params.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> HttpUtil.encodeUrl(entry.getValue())));
    }

    private static String applyParams(String url, Map<String, String> params) {
        String result = url;
        for (Map.Entry<String, String> param: params.entrySet()) {
            result = result.replace(param.getKey(), param.getValue());
        }
        return result;
    }
}
