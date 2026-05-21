package org.prebid.server.bidder;

import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UsersyncInfoFactory {

    private static final String CALLBACK_URL_TEMPLATE = """
            %s/setuid?\
            bidder=%s\
            &gdpr={{gdpr}}\
            &gdpr_consent={{gdpr_consent}}\
            &us_privacy={{us_privacy}}\
            &gpp={{gpp}}\
            &gpp_sid={{gpp_sid}}\
            &f={{format}}\
            &uid=%s""";

    private static final String GDPR_PLACEHOLDER = "{{gdpr}}";
    private static final String GDPR_CONSENT_PLACEHOLDER = "{{gdpr_consent}}";
    private static final String US_PRIVACY_PLACEHOLDER = "{{us_privacy}}";
    private static final String GPP_PLACEHOLDER = "{{gpp}}";
    private static final String GPP_SID_PLACEHOLDER = "{{gpp_sid}}";
    private static final String REDIRECT_URL_PLACEHOLDER = "{{redirect_url}}";
    private static final String FORMAT_PLACEHOLDER = "{{format}}";

    private final String externalUrl;

    public UsersyncInfoFactory(String externalUrl) {
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    public UsersyncInfo build(String bidder,
                              String hostCookieUid,
                              UsersyncMethod usersyncMethod,
                              Privacy privacy) {

        final UsersyncUrls usersyncUrls = buildUsersyncUrls(bidder, hostCookieUid, usersyncMethod);
        final UsersyncUrls usersyncUrlsWithPrivacy = applyPrivacy(usersyncUrls, privacy);
        final String resolvedUsersyncUrl = usersyncUrlsWithPrivacy.usersyncUrl.replace(
                REDIRECT_URL_PLACEHOLDER, HttpUtil.encodeUrl(usersyncUrlsWithPrivacy.redirectUrl));

        return UsersyncInfo.of(resolvedUsersyncUrl, usersyncMethod.getType(), usersyncMethod.isSupportCORS());
    }

    private UsersyncUrls buildUsersyncUrls(String bidder, String hostCookieUid, UsersyncMethod usersyncMethod) {
        final String usersyncUrl = hostCookieUid == null
                ? StringUtils.defaultString(usersyncMethod.getUsersyncUrl())
                : buildRedirectUrl(bidder, hostCookieUid, usersyncMethod);

        final String redirectUrl = hostCookieUid == null
                ? buildRedirectUrl(bidder, usersyncMethod.getUidMacro(), usersyncMethod)
                : StringUtils.EMPTY;

        return UsersyncUrls.of(usersyncUrl, redirectUrl);
    }

    private String buildRedirectUrl(String bidder, String uid, UsersyncMethod method) {
        final String url = CALLBACK_URL_TEMPLATE.formatted(
                externalUrl, HttpUtil.encodeUrl(bidder), StringUtils.defaultString(uid));
        return url.replace(FORMAT_PLACEHOLDER, resolveFormat(method).name);
    }

    private static UsersyncFormat resolveFormat(UsersyncMethod method) {
        return ObjectUtils.firstNonNull(method.getFormatOverride(), method.getType().format);
    }

    private static UsersyncUrls applyPrivacy(UsersyncUrls usersyncUrls, Privacy privacy) {
        final String gdpr = StringUtils.defaultString(privacy.getGdpr());
        final String consent = StringUtils.defaultString(privacy.getConsentString());
        final String ccpa = StringUtils.defaultString(privacy.getCcpa().getUsPrivacy());
        final String gpp = StringUtils.defaultString(privacy.getGpp());
        final String gppSid = listToString(privacy.getGppSid());

        final String redirectUrl = updateUrlWithPrivacy(usersyncUrls.redirectUrl, gdpr, consent, ccpa, gpp, gppSid);

        final String encodedGdpr = HttpUtil.encodeUrl(gdpr);
        final String encodedConsent = HttpUtil.encodeUrl(consent);
        final String encodedUsPrivacy = HttpUtil.encodeUrl(ccpa);
        final String encodedGpp = HttpUtil.encodeUrl(gpp);
        final String endodedGppSid = HttpUtil.encodeUrl(gppSid);

        final String usersyncUrl = updateUrlWithPrivacy(
                usersyncUrls.usersyncUrl, encodedGdpr, encodedConsent, encodedUsPrivacy, encodedGpp, endodedGppSid);

        return UsersyncUrls.of(usersyncUrl, redirectUrl);
    }

    private static String listToString(List<Integer> gppSid) {
        return CollectionUtils.emptyIfNull(gppSid).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static String updateUrlWithPrivacy(String url,
                                               String gdpr,
                                               String gdprConsent,
                                               String usPrivacy,
                                               String gpp,
                                               String gppSid) {

        return url
                .replace(GDPR_PLACEHOLDER, gdpr)
                .replace(GDPR_CONSENT_PLACEHOLDER, gdprConsent)
                .replace(US_PRIVACY_PLACEHOLDER, usPrivacy)
                .replace(GPP_PLACEHOLDER, gpp)
                .replace(GPP_SID_PLACEHOLDER, gppSid);
    }

    @Value(staticConstructor = "of")
    private static class UsersyncUrls {
        String usersyncUrl;
        String redirectUrl;
    }
}
