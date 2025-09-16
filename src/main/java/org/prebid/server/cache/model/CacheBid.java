package org.prebid.server.cache.model;

import com.iab.openrtb.response.Bid;
import lombok.Value;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.cache.CoreCacheService;

/**
 * Holds the information about cache TTL for particular {@link Bid} to be sent to {@link CoreCacheService}.
 */
@Value(staticConstructor = "of")
public class CacheBid {

    BidInfo bidInfo;

    Integer ttl;
}
