package org.prebid.server.cache.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Holds the information about cache TTL for different bid media types.
 * <p>
 * Used for representing configuration.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class CacheTtl {

    private static final CacheTtl EMPTY = CacheTtl.of(null, null);

    Integer bannerCacheTtl;

    Integer videoCacheTtl;

    public static CacheTtl empty() {
        return EMPTY;
    }
}
