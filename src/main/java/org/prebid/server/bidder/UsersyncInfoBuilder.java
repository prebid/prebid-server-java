package org.prebid.server.bidder;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.stream.Collectors;

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
        final String gdpr = StringUtils.defaultString(privacy.getGdpr());
        final String consent = StringUtils.defaultString(privacy.getConsentString());
        final String ccpa = StringUtils.defaultString(privacy.getCcpa().getUsPrivacy());
        final String gpp = StringUtils.defaultString(privacy.getGpp());
        final String gppSid = toString(privacy.getGppSid());

        redirectUrl = updateUrlWithPrivacy(redirectUrl, gdpr, consent, ccpa, gpp, gppSid);

        final String encodedGdpr = HttpUtil.encodeUrl(gdpr);
        final String encodedConsent = HttpUtil.encodeUrl(consent);
        final String encodedUsPrivacy = HttpUtil.encodeUrl(ccpa);
        final String encodedGpp = HttpUtil.encodeUrl(gpp);
        final String endodedGppSid = HttpUtil.encodeUrl(gppSid);

        usersyncUrl = updateUrlWithPrivacy(
                usersyncUrl, encodedGdpr, encodedConsent, encodedUsPrivacy, encodedGpp, endodedGppSid);

        return this;
    }

    private static String toString(List<Integer> gppSid) {
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
                .replace(UsersyncInfo.GDPR_PLACEHOLDER, gdpr)
                .replace(UsersyncInfo.GDPR_CONSENT_PLACEHOLDER, gdprConsent)
                .replace(UsersyncInfo.US_PRIVACY_PLACEHOLDER, usPrivacy)
                .replace(UsersyncInfo.GPP_PLACEHOLDER, gpp)
                .replace(UsersyncInfo.GPP_SID_PLACEHOLDER, gppSid);
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
