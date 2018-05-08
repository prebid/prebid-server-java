package org.prebid.server.cache;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Used to determine cache IDs targeting keywords should be in response
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheIdInfo {

    /**
     * Cache ID for whole bid
     */
    String cacheId;

    /**
     * Cache ID for VAST
     */
    String videoCacheId;
}
