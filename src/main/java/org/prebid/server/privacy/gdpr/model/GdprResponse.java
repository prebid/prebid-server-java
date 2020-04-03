package org.prebid.server.privacy.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@Deprecated
@AllArgsConstructor(staticName = "of")
@Value
public class GdprResponse {

    /**
     * Defines if user is in GDPR scope flag. Can be useful for analyzing GDPR processing result.
     */
    boolean userInGdprScope;

    /**
     * Gdpr processing result map where key is vendor ID and value is GDPR allowed flag.
     */
    Map<Integer, Boolean> vendorsToGdpr;

    /**
     * Defines a country where user comes from.
     */
    String country;
}
