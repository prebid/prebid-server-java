package org.prebid.server.hooks.modules.intentiq.identity.cache;

import io.vertx.core.Future;
import io.vertx.redis.client.RedisAPI;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Redis-backed {@link IdentityStore} (the default L2 backend). Stores values with a per-entry TTL
 * via {@code SET key value PX <ttlMs>}.
 */
@RequiredArgsConstructor
public class RedisIdentityStore implements IdentityStore {

    private static final String EVICTED_KEYS_FIELD = "evicted_keys:";

    private final RedisAPI redis;

    @Override
    public Future<String> get(String key) {
        return redis.get(key).map(response -> response != null ? response.toString() : null);
    }

    @Override
    public Future<Void> put(String key, String value, long ttlMs) {
        return redis.set(List.of(key, value, "PX", Long.toString(ttlMs))).mapEmpty();
    }

    /** Current key count of the selected DB ({@code DBSIZE}). Instance-wide, not module-scoped. */
    public Future<Long> dbSize() {
        return redis.dbsize().map(response -> response != null ? response.toLong() : 0L);
    }

    /** Cumulative {@code evicted_keys} from {@code INFO stats}. Instance-wide. */
    public Future<Long> evictedKeys() {
        return redis.info(List.of("stats")).map(RedisIdentityStore::parseEvictedKeys);
    }

    private static Long parseEvictedKeys(io.vertx.redis.client.Response response) {
        if (response == null) {
            return 0L;
        }
        for (final String line : response.toString().split("\\r?\\n")) {
            if (line.startsWith(EVICTED_KEYS_FIELD)) {
                try {
                    return Long.parseLong(line.substring(EVICTED_KEYS_FIELD.length()).trim());
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
        }
        return 0L;
    }
}
