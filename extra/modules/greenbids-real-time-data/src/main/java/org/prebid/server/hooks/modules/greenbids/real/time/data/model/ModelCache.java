package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import lombok.Getter;
import org.prebid.server.exception.PreBidException;

import java.util.Map;
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
        String cacheKey = "onnxModelRunner_" + pbuid;

        OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);
        System.out.println(
                "getModelRunner: \n" +
                        "   cacheKey: " + cacheKey + "\n" +
                        "   cachedOnnxModelRunner: " + cachedOnnxModelRunner + "\n" +
                        "   cache: " + cache
        );

        for (Map.Entry<String, OnnxModelRunner> entry: cache.asMap().entrySet()) {
            System.out.println("\nKey: " + entry.getKey() + ", Value: " + entry.getValue() + "\n");
        }

        if (cachedOnnxModelRunner != null) {
            System.out.println("cachedOnnxModelRunner available");
            return cachedOnnxModelRunner;
        };

        boolean locked = lock.tryLock();
        try {
            if (locked) {
                Blob blob = getBlob();

                System.out.println(
                        "getModelRunner: \n" +
                                "   blob: " + blob + "\n" +
                                "   cache: " + cache
                );

                cache.put(cacheKey, loadModelRunner(blob));
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
                            "   storage: " + storage + "\n" +
                            "   gcsBucketName: " + gcsBucketName + "\n" +
                            "   modelPath: " + modelPath + "\n"
            );
            Bucket bucket = storage.get(gcsBucketName);
            return bucket.get(modelPath);
        } catch (StorageException e) {
            System.out.println("Error accessing GCS artefact for model: " + e);
            throw new PreBidException("Error accessing GCS artefact for model: ", e);
        }
    }

    private OnnxModelRunner loadModelRunner(Blob blob) {
        try {
            byte[] onnxModelBytes = blob.getContent();
            return new OnnxModelRunner(onnxModelBytes);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load ONNX model", e);
        }
    }
}
