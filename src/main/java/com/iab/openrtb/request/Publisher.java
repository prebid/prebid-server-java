package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;

import java.util.List;

/**
 * This object describes the entity who directly supplies inventory to and is paid
 * by the exchange.This may be a publisher, intermediary exchange, ad network, etc.
 */
@Builder(toBuilder = true)
@Value
public class Publisher {

    /**
     * Exchange-specific seller ID. Every ID must map to only a single entity
     * that is paid for inventory transacted via that ID. Corresponds to a
     * seller_id of a seller in the exchange’s sellers.json file.
     */
    String id;

    /**
     * Seller name (may be aliased at the seller’s request).
     */
    String name;

    /**
     * The taxonomy in use. Refer to the AdCOM list <a href="https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list_categorytaxonomies">List: Category
     * Taxonomies</a> for values.
     */
    Integer cattax;

    /**
     * Array of IAB content categories that describe the publisher.
     * The taxonomy to be used is defined by the cattax field.
     * If no cattax field is supplied IAB Content Category Taxonomy 1.0 is assumed.
     */
    List<String> cat;

    /**
     * Highest level domain of the seller (e.g., “seller.com”).
     */
    String domain;

    /**
     * Placeholder for exchange-specific extensions to OpenRTB.
     */
    ExtPublisher ext;
}
