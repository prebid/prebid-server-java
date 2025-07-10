package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import io.vertx.core.Future;
import org.prebid.server.cache.PbcStorageService;
import org.prebid.server.cache.proto.request.module.StorageDataType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.json.JacksonMapper;

import java.util.Objects;

public class Cache {

    private static final String APP_CODE = "prebid-Java";
    private static final String APPLICATION = "optable-targeting";

    private final PbcStorageService cacheService;
    private final JacksonMapper mapper;

    public Cache(PbcStorageService cacheService, JacksonMapper mapper) {
        this.cacheService = Objects.requireNonNull(cacheService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Future<TargetingResult> get(String query) {
        return cacheService.retrieveEntry(query, APP_CODE, APPLICATION)
                .map(ModuleCacheResponse::getValue)
                .map(body -> body != null ? mapper.decodeValue(body, TargetingResult.class) : null);
    }

    public Future<Void> put(String query, TargetingResult value, int ttlSeconds) {
        if (value == null) {
            return Future.succeededFuture();
        }

        return cacheService.storeEntry(
                query,
                mapper.encodeToString(value),
                StorageDataType.TEXT,
                ttlSeconds,
                APPLICATION,
                APP_CODE);
    }
}
