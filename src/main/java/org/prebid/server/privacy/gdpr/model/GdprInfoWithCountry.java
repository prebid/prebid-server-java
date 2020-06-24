package org.prebid.server.privacy.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Internal class for holding GDPR information and country.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class GdprInfoWithCountry<T> {

    String gdpr;

    T consent;

    String country;

    Boolean inEea;

    public static <T> GdprInfoWithCountry<T> of(String gdpr, T consent) {
        return of(gdpr, consent, null, null);
    }
}

