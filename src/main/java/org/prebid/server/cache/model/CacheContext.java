package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds the state needed to perform caching response bids.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheContext {

    boolean shouldCacheBids;

    Integer cacheBidsTtl;

    boolean shouldCacheVideoBids;

    Integer cacheVideoBidsTtl;
}
