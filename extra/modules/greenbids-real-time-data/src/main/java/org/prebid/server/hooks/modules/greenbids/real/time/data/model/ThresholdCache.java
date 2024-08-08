package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ThresholdCache {

    long CACHE_EXPIRATION_MINUTES = 15;
    String gcsBucketName;
    String thresholdPath;
    Cache<String, ThrottlingThresholds> cache;
    Storage storage;
    ReentrantLock lock;
    ObjectMapper mapper;

    public ThresholdCache(String thresholdPath, Storage storage, String gcsBucketName, ObjectMapper mapper) {
        this.gcsBucketName = gcsBucketName;
        this.thresholdPath = thresholdPath;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build();
        this.storage = storage;
        this.lock = new ReentrantLock();
        this.mapper = mapper;
    }

    public ThrottlingThresholds getThrottlingThresholds() {
        ThrottlingThresholds cachedThrottlingThresholds = cache.getIfPresent("throttlingThresholds");
        if (cachedThrottlingThresholds != null) {
            return cachedThrottlingThresholds;
        };

        boolean locked = lock.tryLock();
        try {
            if (locked) {
                Blob blob = getBlob();

                System.out.println(
                        "getThrottlingThresholds: \n" +
                                "blob: " + blob + "\n" +
                                "cache: " + cache
                );

                cache.put("throttlingThresholds", loadThrottlingThresholds(blob));
            } else {
                System.out.println("Another thread is updating the cache. Skipping fetching predictor.");
                return null;
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }

        return cache.getIfPresent("throttlingThresholds");
    }

    private Blob getBlob() {
        System.out.println(
                "getBlob: \n" +
                        "storage: " + storage + "\n" +
                        "gcsBucketName: " + gcsBucketName + "\n" +
                        "thresholdPath: " + thresholdPath + "\n"
        );
        return storage.get(gcsBucketName).get(thresholdPath);
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
