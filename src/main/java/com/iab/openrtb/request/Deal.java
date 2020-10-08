package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * This object constitutes a specific deal that was struck <em>a priori</em>
 * between a buyer and a seller. Its presence with the {@link Pmp} collection
 * indicates that this impression is available under the terms of that deal.
 * Refer to Section 7.3 for more details.
 */
@Builder(toBuilder = true)
@Value
public class Deal {

    /**
     * A unique identifier for the direct deal. (required)
     */
    String id;

    /**
     * Minimum bid for this impression expressed in CPM.
     */
    float bidfloor;

    /**
     * Currency specified using ISO-4217 alpha codes. This may be different from
     * bid currency returned by bidder if this is allowed by the exchange.
     */
    String bidfloorcur;

    /**
     * Optional override of the overall auction type of the bid request,
     * where 1 = First Price, 2 = Second Price Plus, 3 = the value passed in
     * {@code bidfloor} is the agreed upon deal price. Additional auction types
     * can be defined by the exchange.
     */
    Integer at;

    /**
     * Whitelist of buyer seats (e.g., advertisers, agencies) allowed to bid on
     * this deal. IDs of seats and the buyer’s customers to which they refer
     * must be coordinated between bidders and the exchange a priori.
     * Omission implies no seat restrictions.
     */
    List<String> wseat;

    /**
     * Array of advertiser domains (e.g., advertiser.com) allowed to bid on this
     * deal. Omission implies no advertiser restrictions.
     */
    List<String> wadomain;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ObjectNode ext;
}
