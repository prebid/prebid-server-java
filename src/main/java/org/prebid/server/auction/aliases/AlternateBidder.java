package org.prebid.server.auction.aliases;

import java.util.Set;

public interface AlternateBidder {

    Boolean getEnabled();

    Set<String> getAllowedBidderCodes();
}
