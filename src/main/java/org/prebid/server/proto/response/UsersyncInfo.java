package org.prebid.server.proto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Basic info the browser needs in order to run a user sync.
 * <p>
 * For more information about user syncs, see http://clearcode.cc/2015/12/cookie-syncing/
 */
@Value(staticConstructor = "of")
public class UsersyncInfo {

    public static final String GDPR_PLACEHOLDER = "{{gdpr}}";
    public static final String GDPR_CONSENT_PLACEHOLDER = "{{gdpr_consent}}";
    public static final String US_PRIVACY_PLACEHOLDER = "{{us_privacy}}";

    String url;

    String type;

    @JsonProperty("supportCORS")
    Boolean supportCORS;
}
