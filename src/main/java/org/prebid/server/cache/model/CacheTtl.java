package org.prebid.server.cache.model;

import lombok.Value;

/**
 * Holds the information about cache TTL for different bid media types.
 * <p>
 * Used for representing configuration.
 */
@Value(staticConstructor = "of")
public class CacheTtl {

    Integer bannerCacheTtl;

    Integer videoCacheTtl;
}
