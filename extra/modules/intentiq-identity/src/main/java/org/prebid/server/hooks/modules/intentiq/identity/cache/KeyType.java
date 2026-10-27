package org.prebid.server.hooks.modules.intentiq.identity.cache;

/**
 * Classifies a cache key by the kind of identifier it carries, so the TTL ceiling can be applied
 * per id class (see {@link CacheTtlPolicy}). First-party ids are treated as longer-lived than
 * third-party / probabilistic ones. Note {@code intentiq.com} is treated as {@link #THIRD_PARTY},
 * matching how the IntentIQ backend classifies the IntentIQ cookie id.
 */
public enum KeyType {

    FIRST_PARTY,
    THIRD_PARTY,
    DEVICE
}
