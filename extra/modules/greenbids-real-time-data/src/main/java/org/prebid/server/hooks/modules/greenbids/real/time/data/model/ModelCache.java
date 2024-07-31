package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.File;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ModelCache {

    long CACHE_EXPIRATION_MINUTES = 15;
    String modelPath;
    Cache<String, OnnxModelRunner> cache;
    File modelFile;
    Instant lastModifiedTime;

    public ModelCache(String modelPath) {
        this.modelPath = modelPath;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build();
        this.modelFile = new File(modelPath);
        this.lastModifiedTime = Instant.ofEpochMilli(modelFile.lastModified());
    }

    public OnnxModelRunner getModelRunner() {
        Instant currentLastModifiedTime = Instant.ofEpochMilli(modelFile.lastModified());

        if (!lastModifiedTime.equals(currentLastModifiedTime)) {
            cache.invalidateAll();
            lastModifiedTime = currentLastModifiedTime;
        }

        return cache.get("onnxModelRunner", key -> loadModelRunner());
    }

    private OnnxModelRunner loadModelRunner() {
        try {
            return new OnnxModelRunner(modelPath);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load ONNX model", e);
        }
    }
}
