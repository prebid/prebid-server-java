package org.prebid.server.hooks.modules.intentiq.identity.cache;

import io.vertx.core.Future;

/**
 * Generic, backend-agnostic key/value store used as the shared (L2) layer of {@link IdentityCache}.
 * The default implementation is Redis ({@link RedisIdentityStore}); a partner can provide a different
 * backend by supplying another implementation. Values are opaque strings (the cache handles
 * serialization), so the store stays decoupled from the eid model.
 */
public interface IdentityStore {

    Future<String> get(String key);

    Future<Void> put(String key, String value, long ttlMs);
}
