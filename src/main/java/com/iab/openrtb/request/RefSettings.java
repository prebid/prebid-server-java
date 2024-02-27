package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class RefSettings {

    /**
     * The type of the declared auto refresh.
     */
    Integer reftype;

    /**
     * The minimum refresh interval in seconds. This applies to all refresh types.
     * This is the (uninterrupted) time the ad creative will be rendered
     * before refreshing to the next creative. If the field is absent, the exposure time is unknown.
     * This field does not account for viewability or external factors such as a user leaving a page.
     */
    Integer minint;

    /**
     * Placeholder for vendor specific extensions to this object.
     */
    ObjectNode ext;
}
