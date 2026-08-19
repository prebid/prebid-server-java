package org.prebid.server.hooks.modules.intentiq.identity.cache;

/**
 * A single namespaced cache key derived from a first-party identifier on the bid request, together
 * with its {@link KeyType} (used to pick the TTL ceiling). A request yields an ordered list of these;
 * the resolved identity is aliased across all of them.
 */
public record CacheKey(String key, KeyType type) {
}
