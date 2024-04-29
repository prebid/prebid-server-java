package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtDooh;

import java.util.List;

/**
 * This object should be included if the ad supported content is a Digital Out-Of-Home screen.
 * A bid request with a DOOH object must not contain a site or app object.
 * At a minimum, it is useful to provide id and/or venuetypeid, but this is not strictly required.
 */
@Value
@Builder(toBuilder = true)
public class Dooh {

    /**
     * Exchange provided id for a placement or logical grouping of placements.
     */
    String id;

    /**
     * Name of the dooh placement.
     */
    String name;

    /**
     * The type of out-of-home venue. The taxonomy to be used is defined by the venuetax field.
     * If no venuetax field is supplied, The OpenOOH Venue Taxonomy is assumed.
     */
    List<String> venuetype;

    /**
     * The venue taxonomy in use.
     */
    Integer venuetypetax;

    /**
     * Details about the publisher of the placement.
     */
    Publisher publisher;

    /**
     * Domain of the inventory owner (e.g., “mysite.foo.com”)
     */
    String domain;

    /**
     * Comma separated list of keywords about the DOOH placement.
     */
    String keywords;

    /**
     * Details about the Content within the DOOH placement.
     */
    Content content;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ExtDooh ext;
}
