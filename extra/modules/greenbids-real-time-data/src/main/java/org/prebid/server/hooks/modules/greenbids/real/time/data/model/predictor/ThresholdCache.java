package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholdsFactory;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThresholdCache {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdCache.class);

    private final String gcsBucketName;

    private final Cache<String, ThrottlingThresholds> cache;

    private final Storage storage;

    private final ObjectMapper mapper;

    private final String thresholdsCacheKeyPrefix;

    private final AtomicBoolean isFetching;

    private final Vertx vertx;

    private final ThrottlingThresholdsFactory throttlingThresholdsFactory;

    public ThresholdCache(
            Storage storage,
            String gcsBucketName,
            ObjectMapper mapper,
            Cache<String, ThrottlingThresholds> cache,
            String thresholdsCacheKeyPrefix,
            Vertx vertx,
            ThrottlingThresholdsFactory throttlingThresholdsFactory) {
        this.gcsBucketName = Objects.requireNonNull(gcsBucketName);
        this.cache = Objects.requireNonNull(cache);
        this.storage = Objects.requireNonNull(storage);
        this.mapper = Objects.requireNonNull(mapper);
        this.thresholdsCacheKeyPrefix = Objects.requireNonNull(thresholdsCacheKeyPrefix);
        this.isFetching = new AtomicBoolean(false);
        this.vertx = Objects.requireNonNull(vertx);
        this.throttlingThresholdsFactory = Objects.requireNonNull(throttlingThresholdsFactory);
    }

    public Future<ThrottlingThresholds> get(String thresholdJsonPath, String pbuid) {
        final String cacheKey = thresholdsCacheKeyPrefix + pbuid;
        final ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent(cacheKey);

        if (cachedThrottlingThresholds != null) {
            return Future.succeededFuture(cachedThrottlingThresholds);
        }

        if (isFetching.compareAndSet(false, true)) {
            try {
                return fetchAndCacheThrottlingThresholds(thresholdJsonPath, cacheKey);
            } finally {
                isFetching.set(false);
            }
        }

        return Future.failedFuture("ThrottlingThresholds fetching in progress. Skip current request");
    }

    private Future<ThrottlingThresholds> fetchAndCacheThrottlingThresholds(String thresholdJsonPath, String cacheKey) {
        return vertx.executeBlocking(() -> getBlob(thresholdJsonPath))
                .map(this::loadThrottlingThresholds)
                .onSuccess(thresholds -> cache.put(cacheKey, thresholds))
                .onFailure(error -> logger.error("Failed to fetch thresholds"));
    }

    private Blob getBlob(String thresholdJsonPath) {
        try {
            return Optional.ofNullable(storage.get(gcsBucketName))
                    .map(bucket -> bucket.get(thresholdJsonPath))
                    .orElseThrow(() -> new PreBidException("Bucket not found: " + gcsBucketName));
        } catch (StorageException e) {
            throw new PreBidException("Error accessing GCS artefact for threshold: ", e);
        }
    }

    private ThrottlingThresholds loadThrottlingThresholds(Blob blob) {
        try {
            final byte[] jsonBytes = blob.getContent();
            return throttlingThresholdsFactory.create(jsonBytes, mapper);
        } catch (IOException e) {
            throw new PreBidException("Failed to load throttling thresholds json", e);
        }
    }
}
