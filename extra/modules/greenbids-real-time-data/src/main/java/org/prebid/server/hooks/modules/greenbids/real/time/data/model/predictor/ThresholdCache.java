package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThresholdCache {

    String gcsBucketName;

    String thresholdPath;

    Cache<String, ThrottlingThresholds> cache;

    Storage storage;

    ObjectMapper mapper;

    String thresholdsCacheKeyPrefix;

    ExecutorService executorService;

    AtomicBoolean isFetching;

    public ThresholdCache(
            String thresholdPath,
            Storage storage,
            String gcsBucketName,
            ObjectMapper mapper,
            Cache<String, ThrottlingThresholds> cache,
            String thresholdsCacheKeyPrefix) {
        this.gcsBucketName = gcsBucketName;
        this.thresholdPath = thresholdPath;
        this.cache = cache;
        this.storage = storage;
        this.mapper = mapper;
        this.thresholdsCacheKeyPrefix = thresholdsCacheKeyPrefix;
        this.executorService = Executors.newCachedThreadPool();
        this.isFetching = new AtomicBoolean(false);
    }

    public ThrottlingThresholds getThrottlingThresholds(String pbuid) {
        final String cacheKey = thresholdsCacheKeyPrefix + pbuid;
        final ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent(cacheKey);

        if (cachedThrottlingThresholds != null) {
            return cachedThrottlingThresholds;
        }

        if (isFetching.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    fetchAndCacheThrottlingThresholds(cacheKey);
                } finally {
                    isFetching.set(false);
                }
            }, executorService);
        }
        return null;
    }

    private void fetchAndCacheThrottlingThresholds(String cacheKey) {
        final Blob blob = getBlob();
        final ThrottlingThresholds throttlingThresholds = loadThrottlingThresholds(blob);
        cache.put(cacheKey, throttlingThresholds);
    }

    private Blob getBlob() {
        try {
            return storage.get(gcsBucketName).get(thresholdPath);
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
