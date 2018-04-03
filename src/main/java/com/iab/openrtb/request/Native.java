package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * This object represents a native type impression. Native ad units are intended
 * to blend seamlessly into the surrounding content (e.g., a sponsored Twitter
 * or Facebook post). As such, the response must be well-structured to afford
 * the publisher fine-grained control over rendering.
 * <p>The Native Subcommittee has developed a companion specification to OpenRTB
 * called the Dynamic Native Ads API. It defines the request parameters and
 * response markup structure of native ad units. This object provides the means
 * of transporting request parameters as an opaque string so that the specific
 * parameters can evolve separately under the auspices of the Dynamic Native Ads
 * API. Similarly, the ad markup served will be structured according to that
 * specification.
 * <p>The presence of a Native as a subordinate of the {@link Imp} object
 * indicates that this impression is offered as a native type impression. At the
 * publisher’s discretion, that same impression may also be offered as banner,
 * video, and/or audio by also including as {@link Imp} subordinates objects of
 * those types. However, any given bid for the impression must conform to one of
 * the offered types.
 */
@Data
@Builder
public class Native {

    /** Request payload complying with the Native Ad Specification. (required) */
    String request;

    /**
     * Version of the Dynamic Native Ads API to which request complies; highly
     * recommended for efficient parsing. (recommended)
     */
    String ver;

    /**
     * List of supported API frameworks for this impression. Refer to List 5.6.
     * If an API is not explicitly listed, it is assumed not to be supported.
     */
    List<Integer> api;

    /** Blocked creative attributes. Refer to List 5.3. */
    List<Integer> battr;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ObjectNode ext;
}
