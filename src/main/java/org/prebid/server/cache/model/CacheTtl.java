package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds the information about cache TTL for different bid media types.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheTtl {

    Integer bannerCacheTtl;

    Integer videoCacheTtl;

    public static CacheTtl empty() {
        return CacheTtl.of(null, null);
    }
}
