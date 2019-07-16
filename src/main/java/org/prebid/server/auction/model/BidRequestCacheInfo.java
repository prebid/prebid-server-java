package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;

/**
 * Holds caching information extracted from incoming auction request.
 */
@Builder
@Value
public class BidRequestCacheInfo {

    private static final BidRequestCacheInfo NO_CACHE = BidRequestCacheInfo.builder()
            .doCaching(false)
            .shouldCacheBids(false)
            .cacheBidsTtl(null)
            .shouldCacheVideoBids(false)
            .cacheVideoBidsTtl(null)
            .returnCreativeBids(false)
            .returnCreativeVideoBids(false)
            .build();

    boolean doCaching;

    boolean shouldCacheBids;

    Integer cacheBidsTtl;

    boolean shouldCacheVideoBids;

    Integer cacheVideoBidsTtl;

    boolean returnCreativeBids;

    boolean returnCreativeVideoBids;

    public static BidRequestCacheInfo noCache() {
        return NO_CACHE;
    }
}
