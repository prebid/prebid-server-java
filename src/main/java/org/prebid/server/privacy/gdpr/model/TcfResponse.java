package org.prebid.server.privacy.gdpr.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class TcfResponse<T> {

    /**
     * Defines if user is in GDPR scope flag. Can be useful for analyzing GDPR processing result.
     */
    Boolean userInGdprScope;

    /**
     * Gdpr processing result map where key is vendor ID or bidder name and value is permissions.
     */
    Map<T, PrivacyEnforcementAction> actions;

    /**
     * Defines a country where user comes from.
     */
    String country;
}
