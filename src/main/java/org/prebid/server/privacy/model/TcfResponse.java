package org.prebid.server.privacy.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class TcfResponse {

    /**
     * Defines if user is in GDPR scope flag. Can be useful for analyzing GDPR processing result.
     */
    boolean userInGdprScope;

    /**
     * Gdpr processing result map where key is vendor ID and value is GDPR allowed flag.
     */
    Map<String, PrivacyEnforcementAction> vendorsToGdpr;

    /**
     * Defines a country where user comes from.
     */
    String country;
}
