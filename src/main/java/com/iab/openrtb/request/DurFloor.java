package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * This object allows sellers to specify price floors for video and audio creatives, whose price varies based on time.
 */
@Value
@Builder
public class DurFloor {

    /**
     * An integer indicating the low end of a duration range.
     * If this value is missing, the low end is unbounded. Either mindur or maxdur is required, but not both.
     */
    Integer mindur;

    /**
     * An integer indicating the high end of a duration range.
     * If this value is missing, the high end is unbounded. Either mindur or maxdur is required, but not both.
     */
    Integer maxdur;

    /**
     * Minimum bid for a given impression opportunity,
     * if bidding with a creative in this duration range, expressed in CPM.
     * For any creatives whose durations are outside the defined min/max,
     * the `bidfloor` at the `Imp` level will serve as the default floor.
     */
    BigDecimal bidfloor;

    /**
     * Placeholder for vendor specific extensions to this object
     */
    ObjectNode ext;

}
