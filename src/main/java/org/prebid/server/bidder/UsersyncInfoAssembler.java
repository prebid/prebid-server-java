package org.prebid.server.bidder;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.util.HttpUtil;

public class UsersyncInfoAssembler {

    private String usersyncUrl;
    private String redirectUrl;
    private UsersyncMethodType type;
    private Boolean supportCORS;

    public static UsersyncInfoAssembler from(UsersyncMethod usersyncMethod) {
        final UsersyncInfoAssembler usersyncInfoAssembler = new UsersyncInfoAssembler();
        usersyncInfoAssembler.usersyncUrl = usersyncMethod.getUsersyncUrl();
        usersyncInfoAssembler.redirectUrl = UsersyncUtil.enrichUsersyncUrlWithFormat(
                StringUtils.stripToEmpty(usersyncMethod.getRedirectUrl()),
                usersyncMethod.getType());
        usersyncInfoAssembler.type = usersyncMethod.getType();
        usersyncInfoAssembler.supportCORS = usersyncMethod.isSupportCORS();
        return usersyncInfoAssembler;
    }

    /**
     * Updates with already fully build usersync url with possible redirection.
     * Erased redirect url, as usersync url is already completed
     */
    public UsersyncInfoAssembler withUrl(String usersyncUrl) {
        this.usersyncUrl = usersyncUrl;
        this.redirectUrl = StringUtils.EMPTY;
        return this;
    }

    /**
     * Updates url to be GDPR-aware. It will replace:
     * <p>
     * {{gdpr}} -- with the "gdpr" string (should be either "0", "1", or "")
     * {{gdpr_consent}} -- with the Raw base64 URL-encoded GDPR Vendor Consent string.
     * {{us_privacy}} -- with the URL-encoded "us_privacy" string.
     * <p>
     * For example, the template:
     * //some-domain.com/getuid?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}&us_privacy={{us_privacy}}&callback=prebid-server-domain.com%2Fsetuid%3Fbidder%3Dadnxs%26gdpr={{gdpr}}%26gdpr_consent={{gdpr_consent}}%26us_privacy={{us_privacy}}%26uid%3D%24UID
     * <p>
     * would evaluate to:
     * //some-domain.com/getuid?gdpr=&gdpr_consent=BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw&us_privacy=1YN&callback=prebid-server-domain.com%2Fsetuid%3Fbidder%3Dadnxs%26gdpr=%26gdpr_consent=BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw%26us_privacy=1YN%26%26uid%3D%24UID
     * <p>
     * if the "gdpr" arg was empty, and the consent arg was "BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw"
     */
    public UsersyncInfoAssembler withPrivacy(Privacy privacy) {
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

    public UsersyncInfo assemble() {
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
