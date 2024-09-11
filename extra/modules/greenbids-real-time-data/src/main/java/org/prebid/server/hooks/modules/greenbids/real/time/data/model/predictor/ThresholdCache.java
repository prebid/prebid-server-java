package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThresholdCache {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdCache.class);

    String gcsBucketName;

    Cache<String, ThrottlingThresholds> cache;

    Storage storage;

    ObjectMapper mapper;

    String thresholdsCacheKeyPrefix;

    AtomicBoolean isFetching;

    Vertx vertx;

    public ThresholdCache(
            Storage storage,
            String gcsBucketName,
            ObjectMapper mapper,
            Cache<String, ThrottlingThresholds> cache,
            String thresholdsCacheKeyPrefix,
            Vertx vertx) {
        this.gcsBucketName = gcsBucketName;
        this.cache = cache;
        this.storage = storage;
        this.mapper = mapper;
        this.thresholdsCacheKeyPrefix = thresholdsCacheKeyPrefix;
        this.isFetching = new AtomicBoolean(false);
        this.vertx = vertx;
    }

    public Future<ThrottlingThresholds> getThrottlingThresholds(String thresholdJsonPath, String pbuid) {
        final String cacheKey = thresholdsCacheKeyPrefix + pbuid;
        final ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent(cacheKey);

        if (cachedThrottlingThresholds != null) {
            return Future.succeededFuture(cachedThrottlingThresholds);
        }

        if (isFetching.compareAndSet(false, true)) {
            try {
                fetchAndCacheThrottlingThresholds(thresholdJsonPath, cacheKey);
            } finally {
                isFetching.set(false);
            }
        }

        return Future.failedFuture("ThrottlingThresholds fetching in progress");
    }

    private void fetchAndCacheThrottlingThresholds(String thresholdJsonPath, String cacheKey) {
        vertx.executeBlocking(promise -> {
                    Blob blob = getBlob(thresholdJsonPath);
                    promise.complete(blob);
                })
                .map(blob -> loadThrottlingThresholds((Blob) blob))
                .onSuccess(thresholds -> cache.put(cacheKey, thresholds))
                .onFailure(error -> logger.error("Failed to fetch thresholds"));;
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
        final JsonNode thresholdsJsonNode;
        try {
            final byte[] jsonBytes = blob.getContent();
            thresholdsJsonNode = mapper.readTree(jsonBytes);
            return mapper.treeToValue(thresholdsJsonNode, ThrottlingThresholds.class);
        } catch (IOException e) {
            throw new PreBidException("Failed to load throttling thresholds json", e);
        }
    }
}
