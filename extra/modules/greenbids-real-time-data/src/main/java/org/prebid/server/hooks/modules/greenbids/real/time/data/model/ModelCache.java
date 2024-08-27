package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import lombok.Getter;
import org.prebid.server.exception.PreBidException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModelCache {

    String gcsBucketName;

    String modelPath;

    @Getter
    Cache<String, OnnxModelRunner> cache;

    Storage storage;

    String onnxModelCacheKeyPrefix;

    ExecutorService executorService;

    public ModelCache(
            String modelPath,
            Storage storage,
            String gcsBucketName,
            Cache<String, OnnxModelRunner> cache,
            String onnxModelCacheKeyPrefix) {
        this.gcsBucketName = gcsBucketName;
        this.modelPath = modelPath;
        this.cache = cache;
        this.storage = storage;
        this.onnxModelCacheKeyPrefix = onnxModelCacheKeyPrefix;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public OnnxModelRunner getModelRunner(String pbuid) {
        final String cacheKey = onnxModelCacheKeyPrefix + pbuid;
        final OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);

        if (cachedOnnxModelRunner != null) {
            return cachedOnnxModelRunner;
        }

        CompletableFuture.runAsync(() -> fetchAndCacheModelRunner(cacheKey), executorService);
        return null;
    }

    private void fetchAndCacheModelRunner(String cacheKey) {
        final Blob blob = getBlob();
        final OnnxModelRunner onnxModelRunner = loadModelRunner(blob);
        cache.put(cacheKey, onnxModelRunner);
    }

    private Blob getBlob() {
        try {
            final Bucket bucket = storage.get(gcsBucketName);
            return bucket.get(modelPath);
        } catch (StorageException e) {
            throw new PreBidException("Error accessing GCS artefact for model: ", e);
        }
    }

    private OnnxModelRunner loadModelRunner(Blob blob) {
        try {
            final byte[] onnxModelBytes = blob.getContent();
            return new OnnxModelRunner(onnxModelBytes);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load ONNX model", e);
        }
    }
}
