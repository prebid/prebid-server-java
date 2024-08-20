package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import lombok.Getter;
import org.prebid.server.exception.PreBidException;

import java.util.concurrent.locks.ReentrantLock;

public class ModelCache {

    String gcsBucketName;

    String modelPath;

    @Getter
    Cache<String, OnnxModelRunner> cache;

    Storage storage;

    ReentrantLock lock;

    public ModelCache(
            String modelPath,
            Storage storage,
            String gcsBucketName,
            Cache<String, OnnxModelRunner> cache) {
        this.gcsBucketName = gcsBucketName;
        this.modelPath = modelPath;
        this.cache = cache;
        this.storage = storage;
        this.lock = new ReentrantLock();
    }

    public OnnxModelRunner getModelRunner(String pbuid) {
        final String cacheKey = "onnxModelRunner_" + pbuid;
        final OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);

        if (cachedOnnxModelRunner != null) {
            return cachedOnnxModelRunner;
        }

        final boolean locked = lock.tryLock();
        try {
            if (locked) {
                final Blob blob = getBlob();
                cache.put(cacheKey, loadModelRunner(blob));
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
