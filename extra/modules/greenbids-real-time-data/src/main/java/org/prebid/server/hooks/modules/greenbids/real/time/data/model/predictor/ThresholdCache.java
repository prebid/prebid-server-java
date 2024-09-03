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

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThresholdCache {

    String gcsBucketName;

    String thresholdPath;

    Cache<String, ThrottlingThresholds> cache;

    Storage storage;

    ObjectMapper mapper;

    String thresholdsCacheKeyPrefix;

    AtomicBoolean isFetching;

    Vertx vertx;

    public ThresholdCache(
            String thresholdPath,
            Storage storage,
            String gcsBucketName,
            ObjectMapper mapper,
            Cache<String, ThrottlingThresholds> cache,
            String thresholdsCacheKeyPrefix,
            Vertx vertx) {
        this.gcsBucketName = gcsBucketName;
        this.thresholdPath = thresholdPath;
        this.cache = cache;
        this.storage = storage;
        this.mapper = mapper;
        this.thresholdsCacheKeyPrefix = thresholdsCacheKeyPrefix;
        this.isFetching = new AtomicBoolean(false);
        this.vertx = vertx;
    }

    public Future<ThrottlingThresholds> getThrottlingThresholds(String pbuid) {
        final String cacheKey = thresholdsCacheKeyPrefix + pbuid;
        final ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent(cacheKey);

        if (cachedThrottlingThresholds != null) {
            return Future.succeededFuture(cachedThrottlingThresholds);
        }

        if (isFetching.compareAndSet(false, true)) {
            try {
                fetchAndCacheThrottlingThresholds(cacheKey);
            } finally {
                isFetching.set(false);
            }
        }

        return Future.failedFuture("ThrottlingThresholds fetching in progress");
    }

    private Future<Void> fetchAndCacheThrottlingThresholds(String cacheKey) {
        return vertx.executeBlocking(promise -> {
            try {
                final Blob blob = getBlob();
                final ThrottlingThresholds throttlingThresholds = loadThrottlingThresholds(blob);
                cache.put(cacheKey, throttlingThresholds);
            } catch (RuntimeException e) {
                promise.fail(e);
            }
        });

    }

    private Blob getBlob() {
        try {
            return Optional.ofNullable(storage.get(gcsBucketName))
                    .map(bucket -> bucket.get(thresholdPath))
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
