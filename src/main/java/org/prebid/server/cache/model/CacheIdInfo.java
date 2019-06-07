package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Used to determine cache IDs targeting keywords should be in response
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheIdInfo {

    private static final CacheIdInfo EMPTY = CacheIdInfo.of(null, null);

    /**
     * Cache ID for whole bid
     */
    String cacheId;

    /**
     * Cache ID for VAST
     */
    String videoCacheId;

    public static CacheIdInfo empty() {
        return EMPTY;
    }
}
