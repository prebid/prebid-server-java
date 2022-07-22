package org.prebid.server.bidder;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

public class UsersyncInfoBuilder {

    private String usersyncUrl;
    private String redirectUrl;
    private UsersyncMethodType type;
    private Boolean supportCORS;

    public static UsersyncInfoBuilder from(UsersyncMethod usersyncMethod) {
        final UsersyncInfoBuilder usersyncInfoBuilder = new UsersyncInfoBuilder();
        usersyncInfoBuilder.usersyncUrl = StringUtils.defaultString(usersyncMethod.getUsersyncUrl());
        usersyncInfoBuilder.redirectUrl = UsersyncUtil.enrichUrlWithFormat(
                StringUtils.stripToEmpty(usersyncMethod.getRedirectUrl()),
                UsersyncUtil.resolveFormat(usersyncMethod));
        usersyncInfoBuilder.type = usersyncMethod.getType();
        usersyncInfoBuilder.supportCORS = usersyncMethod.isSupportCORS();

        return usersyncInfoBuilder;
    }

    public UsersyncInfoBuilder usersyncUrl(String usersyncUrl) {
        this.usersyncUrl = StringUtils.defaultString(usersyncUrl);
        return this;
    }

    public UsersyncInfoBuilder redirectUrl(String redirectUrl) {
        this.redirectUrl = StringUtils.defaultString(redirectUrl);
        return this;
    }

    public UsersyncInfoBuilder privacy(Privacy privacy) {
        final String gdpr = ObjectUtils.defaultIfNull(privacy.getGdpr(), "");
        final String consent = ObjectUtils.defaultIfNull(privacy.getConsentString(), "");
        final String ccpa = ObjectUtils.defaultIfNull(privacy.getCcpa().getUsPrivacy(), "");
        redirectUrl = updateUrlWithPrivacy(redirectUrl, gdpr, consent, ccpa);

        final String encodedGdpr = HttpUtil.encodeUrl(gdpr);
        final String encodedConsent = HttpUtil.encodeUrl(consent);
        final String encodedUsPrivacy = HttpUtil.encodeUrl(ccpa);
        usersyncUrl = updateUrlWithPrivacy(usersyncUrl, encodedGdpr, encodedConsent, encodedUsPrivacy);

        return this;
    }

    private static String updateUrlWithPrivacy(String url, String gdpr, String gdprConsent, String usPrivacy) {
        return url
                .replace(UsersyncInfo.GDPR_PLACEHOLDER, gdpr)
                .replace(UsersyncInfo.GDPR_CONSENT_PLACEHOLDER, gdprConsent)
                .replace(UsersyncInfo.US_PRIVACY_PLACEHOLDER, usPrivacy);
    }

    public UsersyncInfo build() {
        final String resolvedRedirectUrl = StringUtils.countMatches(redirectUrl, '?') > 1
                ? resolveQueryParams(redirectUrl)
                : HttpUtil.encodeUrl(redirectUrl);

        final String resolvedUsersyncUrl = usersyncUrl.replace(
                UsersyncInfo.REDIRECT_URL_PLACEHOLDER, resolvedRedirectUrl);

        return UsersyncInfo.of(resolvedUsersyncUrl, type, supportCORS);
    }

    private static String resolveQueryParams(String redirectUrl) {
        final int queryParamsIndex = redirectUrl.lastIndexOf('?');
        final String queryParams = redirectUrl.substring(queryParamsIndex);

        return HttpUtil.encodeUrl(redirectUrl.substring(0, queryParamsIndex)) + queryParams;
    }
}
