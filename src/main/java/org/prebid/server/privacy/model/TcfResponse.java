package org.prebid.server.privacy.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class TcfResponse {

    /**
     * Defines if user is in GDPR scope flag. Can be useful for analyzing GDPR processing result.
     */
    boolean userInGdprScope;

    Map<String, PrivacyEnforcementAction> bidderNameToAction;

    /**
     * Gdpr processing result map where key is vendor ID and value is GDPR allowed flag.
     */
    Map<Integer, PrivacyEnforcementAction> vendorIdToAction;

    /**
     * Defines a country where user comes from.
     */
    String country;
}
