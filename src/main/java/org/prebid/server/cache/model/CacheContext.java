package org.prebid.server.cache.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

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

    Map<String, List<String>> bidderToVideoBidIdsToModify;

    Map<String, List<String>> bidderToBidIds;
}
