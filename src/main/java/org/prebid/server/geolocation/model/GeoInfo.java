package org.prebid.server.geolocation.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class GeoInfo {

    /**
     * Country code in ISO-3166 (https://www.iso.org/glossary-for-iso-3166.html) format.
     */
    String country;
}
