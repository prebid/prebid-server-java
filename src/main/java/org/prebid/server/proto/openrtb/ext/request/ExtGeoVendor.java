package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidrequest.device.geo.ext.&lt;vendor&gt; or bidrequest.user.geo.ext.&lt;vendor&gt;
 */
@Builder(toBuilder = true)
@Value
public class ExtGeoVendor {

    public static final ExtGeoVendor EMPTY = ExtGeoVendor.builder().build();

    /**
     * Continent code in two-letter format:
     * <p>
     * af - Africa, an - Antarctica, as - Asia, eu - Europe, na - North America, oc - Australia, sa - South America.
     */
    String continent;

    /**
     * Country code in ISO-3166-1-alpha-2 format.
     */
    String country;

    Integer region;

    /**
     * Nielson DMA code (not Google).
     */
    Integer metro;

    String city;

    String zip;
}
