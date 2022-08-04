package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * This object is the private marketplace container for direct deals between
 * buyers and sellers that may pertain to this impression. The actual deals are
 * represented as a collection of {@link Deal} objects.
 * Refer to Section 7.3 for more details.
 */
@Builder(toBuilder = true)
@Value
public class Pmp {

    /**
     * Indicator of auction eligibility to seats named in the Direct Deals
     * object, where 0 = all bids are accepted, 1 = bids are restricted to the
     * deals specified and the terms thereof.
     */
    Integer privateAuction;

    /**
     * Array of {@link Deal} (Section 3.2.12) objects that convey the specific deals
     * applicable to this impression.
     */
    List<Deal> deals;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
