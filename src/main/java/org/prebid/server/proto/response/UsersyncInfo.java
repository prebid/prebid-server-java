package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.util.HttpUtil;

@AllArgsConstructor(staticName = "of")
@Value
public class UsersyncInfo {

    public static final String GDPR_PLACEHOLDER = "{{gdpr}}";
    public static final String GDPR_CONSENT_PLACEHOLDER = "{{gdpr_consent}}";

    String url;

    String type;

    @JsonProperty("supportCORS")
    Boolean supportCORS;

    /**
     * Updates url to be GDPR-aware. It will replace:
     * <p>
     * {{gdpr}} -- with the "gdpr" string (should be either "0", "1", or "")
     * {{gdpr_consent}} -- with the Raw base64 URL-encoded GDPR Vendor Consent string.
     * <p>
     * For example, the template:
     * //some-domain.com/getuid?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}&callback=prebid-server-domain.com%2Fsetuid%3Fbidder%3Dadnxs%26gdpr={{gdpr}}%26gdpr_consent={{gdpr_consent}}%26uid%3D%24UID
     * <p>
     * would evaluate to:
     * //some-domain.com/getuid?gdpr=&gdpr_consent=BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw&callback=prebid-server-domain.com%2Fsetuid%3Fbidder%3Dadnxs%26gdpr=%26gdpr_consent=BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw%26uid%3D%24UID
     * <p>
     * if the "gdpr" arg was empty, and the consent arg was "BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw"
     */
    public UsersyncInfo withGdpr(String gdpr, String gdprConsent) {
        if (url.contains(GDPR_PLACEHOLDER) && url.contains(GDPR_CONSENT_PLACEHOLDER)) {
            final String updatedUrl = url
                    .replace(GDPR_PLACEHOLDER, HttpUtil.encodeUrl(ObjectUtils.firstNonNull(gdpr, "")))
                    .replace(GDPR_CONSENT_PLACEHOLDER, HttpUtil.encodeUrl(ObjectUtils.firstNonNull(gdprConsent, "")));

            return UsersyncInfo.of(updatedUrl, type, supportCORS);
        }
        return this;
    }
}
