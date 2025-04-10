package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import io.vertx.core.Future;
import org.prebid.server.cache.PbcStorageService;
import org.prebid.server.cache.proto.request.module.StorageDataType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableResponseMapper;

import java.util.Objects;
import java.util.function.Function;

public class Cache {

    private static final String APP_CODE = "prebid-Java";

    private static final String APPLICATION = "optable-targeting";

    private final PbcStorageService cacheService;

    private final OptableResponseMapper optableResponseMapper;

    private final boolean isEnabled;

    private final int ttlSeconds;

    public Cache(PbcStorageService cacheService,
                 OptableResponseMapper optableResponseMapper,
                 boolean isEnabled,
                 int ttlSeconds) {

        this.cacheService = Objects.requireNonNull(cacheService);
        this.optableResponseMapper = Objects.requireNonNull(optableResponseMapper);
        this.isEnabled = isEnabled;
        this.ttlSeconds = ttlSeconds;
    }

    public Future<TargetingResult> get(String query) {
        return cacheService.retrieveEntry(query, APP_CODE, APPLICATION)
                .map(ModuleCacheResponse::getValue)
                .map(optableResponseMapper::parse)
                .otherwise(it -> null);
    }

    public boolean put(String query, TargetingResult value) {
        cacheService.storeEntry(
                query,
                optableResponseMapper.toJsonString(value),
                StorageDataType.TEXT,
                ttlSeconds,
                APPLICATION, APP_CODE);
        return true;
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}
