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
import java.util.Map;
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
        String cacheKey = "throttlingThresholds_" + pbuid;

        ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent(cacheKey);
        System.out.println(
                "getThrottlingThresholds: \n" +
                        "   cacheKey: " + cacheKey + "\n" +
                        "   cachedThrottlingThresholds: " + cachedThrottlingThresholds + "\n" +
                        "   cache: " + cache
        );

        for (Map.Entry<String, ThrottlingThresholds> entry: cache.asMap().entrySet()) {
            System.out.println("\nKey: " + entry.getKey() + ", Value: " + entry.getValue() + "\n");
        }

        if (cachedThrottlingThresholds != null) {
            System.out.println("cachedThrottlingThresholds available");
            return cachedThrottlingThresholds;
        };

        boolean locked = lock.tryLock();
        try {
            if (locked) {
                Blob blob = getBlob();

                System.out.println(
                        "getThrottlingThresholds: \n" +
                                "read blob: " + blob + "\n" +
                                "put in cache: " + cache
                );

                cache.put(cacheKey, loadThrottlingThresholds(blob));
            } else {
                System.out.println("Another thread is updating the cache. Skipping fetching predictor.");
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
            System.out.println(
                    "getBlob: \n" +
                            "storage: " + storage + "\n" +
                            "gcsBucketName: " + gcsBucketName + "\n" +
                            "thresholdPath: " + thresholdPath + "\n"
            );
            return storage.get(gcsBucketName).get(thresholdPath);
        } catch (StorageException e) {
            System.out.println("Error accessing GCS artefact for threshold: " + e);
            throw new PreBidException("Error accessing GCS artefact for threshold: ", e);
        }
    }

    private ThrottlingThresholds loadThrottlingThresholds(Blob blob) {
        JsonNode thresholdsJsonNode;
        try {
            byte[] jsonBytes = blob.getContent();
            thresholdsJsonNode = mapper.readTree(jsonBytes);
            ThrottlingThresholds throttlingThresholds = mapper.treeToValue(thresholdsJsonNode, ThrottlingThresholds.class);
            return throttlingThresholds;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
