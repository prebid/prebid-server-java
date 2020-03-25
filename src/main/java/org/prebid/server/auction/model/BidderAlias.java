package org.prebid.server.auction.model;

import lombok.Value;

/**
 * Represents properties of bidder alias.
 * <p>
 * This class is supposed to be used as a value in aliases {@link java.util.Map} where key is an alias and value is
 * alias properties.
 */
@Value(staticConstructor = "of")
public class BidderAlias {

    String bidder;

    Integer aliasVendorId;
}
