package org.prebid.server.cache.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.GeneratedBidIds;

/**
 * Holds the state needed to perform caching response bids.
 */
@Builder
@Value
public class CacheContext {

    boolean shouldCacheBids;

    Integer cacheBidsTtl;

    boolean shouldCacheVideoBids;

    Integer cacheVideoBidsTtl;

    GeneratedBidIds bidderToVideoGeneratedBidIdsToModify;

    GeneratedBidIds bidderToBidsToGeneratedIds;
}
