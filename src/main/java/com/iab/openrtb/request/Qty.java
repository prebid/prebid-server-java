package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * A programmatic impression is often referred to as a ‘spot’ in digital
 * out-of-home and CTV, with an impression being a unique member of the audience viewing it.
 * Therefore, a standard means of passing a multiplier in the bid request,
 * representing the total quantity of impressions, is required.
 */
@Builder
@Value
public class Qty {

    /**
     * The quantity of billable events which will be deemed to have occurred if this item is purchased.
     * For example, a DOOH opportunity may be considered to be 14.2 impressions. Equivalent to qtyflt in OpenRTB 3.0.
     */
    BigDecimal multiplier;

    /**
     * The source type of the quantity measurement, i.e. publisher.
     */
    Integer sourcetype;

    /**
     * The top level business domain name of the measurement vendor providing the quantity measurement.
     */
    String vendor;

    /**
     * Placeholder for vendor specific extensions to this object.
     */
    ObjectNode ext;
}
