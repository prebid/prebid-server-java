package org.prebid.server.geolocation.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.time.ZoneId;

@Builder
@Value
public class GeoInfo {

    /**
     * Name of the geo location data provider.
     */
    @NonNull
    String vendor;

    /**
     * Continent code in two-letter format.
     */
    String continent;

    /**
     * Country code in ISO-3166-1-alpha-2 format.
     */
    String country;

    /**
     * Region code in ISO-3166-2 format.
     */
    String region;

    /**
     * Numeric region code.
     */
    Integer regionCode;

    String city;

    /**
     * Google Metro code.
     */
    String metroGoogle;

    /**
     * Nielsen Designated Market Areas (DMA's).
     */
    Integer metroNielsen;

    String zip;

    String connectionSpeed;

    Float lat;

    Float lon;

    ZoneId timeZone;
}
