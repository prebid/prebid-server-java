package org.prebid.server.cache.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

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

    List<String> videoBidIdsToModify;
}
