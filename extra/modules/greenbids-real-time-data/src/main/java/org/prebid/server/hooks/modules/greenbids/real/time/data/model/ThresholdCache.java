package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import lombok.Getter;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class ThresholdCache {

    String gcsBucketName;
    String thresholdPath;
    @Getter
    Cache<String, ThrottlingThresholds> cache;
    Storage storage;
    ReentrantLock lock;
    ObjectMapper mapper;

    public ThresholdCache(
            String thresholdPath,
            Storage storage,
            String gcsBucketName,
            ObjectMapper mapper,
            Cache<String, ThrottlingThresholds> cache) {
        this.gcsBucketName = gcsBucketName;
        this.thresholdPath = thresholdPath;
        this.cache = cache;
        this.storage = storage;
        this.lock = new ReentrantLock();
        this.mapper = mapper;
    }

    public ThrottlingThresholds getThrottlingThresholds(String pbuid) {
        final String cacheKey = "throttlingThresholds_" + pbuid;
        final ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent(cacheKey);

        if (cachedThrottlingThresholds != null) {
            return cachedThrottlingThresholds;
        }

        final boolean locked = lock.tryLock();
        try {
            if (locked) {
                final Blob blob = getBlob();
                cache.put(cacheKey, loadThrottlingThresholds(blob));
            } else {
                return null;
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }

        return cache.getIfPresent(cacheKey);
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
            throw new RuntimeException(e);
        }
    }
}
