package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Used to determine cache IDs targeting keywords should be in response
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheInfo {

    private static final CacheInfo EMPTY = CacheInfo.of(null, null, null, null);

    /**
     * Cache ID for whole bid
     */
    String cacheId;

    /**
     * Cache ID for VAST
     */
    String videoCacheId;

    /**
     * Cache TTL
     */
    Integer ttl;

    /**
     * Cache TTL for video
     */
    Integer videoTtl;

    public static CacheInfo empty() {
        return EMPTY;
    }
}
