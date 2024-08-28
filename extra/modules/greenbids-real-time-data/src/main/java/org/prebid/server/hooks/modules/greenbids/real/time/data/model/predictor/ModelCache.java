package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

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
import java.util.concurrent.atomic.AtomicBoolean;

public class ModelCache {

    String gcsBucketName;

    String modelPath;

    @Getter
    Cache<String, OnnxModelRunner> cache;

    Storage storage;

    String onnxModelCacheKeyPrefix;

    ExecutorService executorService;

    AtomicBoolean isFetching;

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
        this.executorService = Executors.newCachedThreadPool();
        this.isFetching = new AtomicBoolean(false);
    }

    public OnnxModelRunner getModelRunner(String pbuid) {
        final String cacheKey = onnxModelCacheKeyPrefix + pbuid;
        final OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);

        if (cachedOnnxModelRunner != null) {
            return cachedOnnxModelRunner;
        }

        if (isFetching.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    fetchAndCacheModelRunner(cacheKey);
                } finally {
                    isFetching.set(false);
                }
            }, executorService);
        }
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
            throw new PreBidException("Failed to load ONNX model", e);
        }
    }
}
