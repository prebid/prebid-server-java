package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ModelCache {

    long CACHE_EXPIRATION_MINUTES = 15;
    String gcsBucketName;
    String modelPath;
    Cache<String, OnnxModelRunner> cache;
    Storage storage;
    ReentrantLock lock;

    public ModelCache(String modelPath, Storage storage, String gcsBucketName) {
        this.gcsBucketName = gcsBucketName;
        this.modelPath = modelPath;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build();
        this.storage = storage;
        this.lock = new ReentrantLock();
    }

    public OnnxModelRunner getModelRunner() {
        OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent("onnxModelRunner");
        if (cachedOnnxModelRunner != null) {
            return cachedOnnxModelRunner;
        };

        boolean locked = lock.tryLock();
        try {
            if (locked) {
                Blob blob = getBlob();

                System.out.println(
                        "getModelRunner: \n" +
                                "blob: " + blob + "\n" +
                                "cache: " + cache
                );

                cache.put("onnxModelRunner", loadModelRunner(blob));
            } else {
                System.out.println("Another thread is updating the cache. Skipping fetching predictor.");
                return null;
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }

        return cache.getIfPresent("onnxModelRunner");
    }

    private Blob getBlob() {
        System.out.println(
                "getBlob: \n" +
                        "storage: " + storage + "\n" +
                        "gcsBucketName: " + gcsBucketName + "\n" +
                        "modelPath: " + modelPath + "\n"
        );
        return storage.get(gcsBucketName).get(modelPath);
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
