package org.prebid.server.privacy.gdpr.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class TcfResponse {

    /**
     * Defines if user is in GDPR scope flag. Can be useful for analyzing GDPR processing result.
     */
    Boolean userInGdprScope;

    /**
     * Gdpr processing result map where key is vendor ID and value is permissions.
     */
    Map<Integer, PrivacyEnforcementAction> vendorIdToActionMap;

    /**
     * Gdpr processing result map where key is bidder name and value is permissions.
     */
    Map<String, PrivacyEnforcementAction> bidderNameToActionMap;

    /**
     * Defines a country where user comes from.
     */
    String country;
}

