package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.util.HttpUtil;

/**
 * Basic info the browser needs in order to run a user sync.
 * <p>
 * For more information about user syncs, see http://clearcode.cc/2015/12/cookie-syncing/
 */
@AllArgsConstructor(staticName = "of")
@Value
public class UsersyncInfo {

    public static final String GDPR_PLACEHOLDER = "{{gdpr}}";
    public static final String GDPR_CONSENT_PLACEHOLDER = "{{gdpr_consent}}";

    String url;

    String type;

    @JsonProperty("supportCORS")
    Boolean supportCORS;

    public static class UsersyncInfoAssembler {
        private String usersyncUrl;
        private String redirectUrl;
        private String type;
        private Boolean supportCORS;

        public static UsersyncInfoAssembler assembler(String usersyncUrl, String redirectUrl, String type,
                                                      Boolean supportCORS) {
            final UsersyncInfoAssembler usersyncInfoAssembler = new UsersyncInfoAssembler();
            usersyncInfoAssembler.usersyncUrl = usersyncUrl;
            usersyncInfoAssembler.redirectUrl = ObjectUtils.firstNonNull(redirectUrl, "");
            usersyncInfoAssembler.type = type;
            usersyncInfoAssembler.supportCORS = supportCORS;
            return usersyncInfoAssembler;
        }

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
        public UsersyncInfoAssembler withGdpr(String gdpr, String gdprConsent) {
            final String nonNullGdpr = ObjectUtils.firstNonNull(gdpr, "");
            final String nonNullGdprConsent = ObjectUtils.firstNonNull(gdprConsent, "");
            usersyncUrl = updateUrlWithGdpr(usersyncUrl, HttpUtil.encodeUrl(nonNullGdpr),
                    HttpUtil.encodeUrl(nonNullGdprConsent));
            redirectUrl = updateUrlWithGdpr(redirectUrl, nonNullGdpr, nonNullGdprConsent);
            return this;
        }

        private String updateUrlWithGdpr(String url, String gdpr, String gdprConsent) {
            return url
                    .replace(GDPR_PLACEHOLDER, gdpr)
                    .replace(GDPR_CONSENT_PLACEHOLDER, gdprConsent);
        }

        public UsersyncInfo assemble() {
            return new UsersyncInfo(usersyncUrl + HttpUtil.encodeUrl(redirectUrl), type, supportCORS);
        }
    }
}
