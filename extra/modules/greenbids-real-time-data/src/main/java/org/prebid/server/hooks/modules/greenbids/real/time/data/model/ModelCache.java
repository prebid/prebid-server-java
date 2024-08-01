package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ModelCache {

    long CACHE_EXPIRATION_MINUTES = 15;
    static String GCS_BUCKET_NAME = "greenbids-europe-west1-prebid-server-staging";
    static String GOOGLE_CLOUD_GREENBIDS_PROJECT = "greenbids-357713";
    String modelPath;
    Cache<String, OnnxModelRunner> cache;
    Instant lastModifiedTime;
    Storage storage;

    public ModelCache(String modelPath) {
        this.modelPath = modelPath;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build();
        // force cache load on first access
        this.lastModifiedTime = Instant.EPOCH;
        this.storage = StorageOptions.newBuilder().setProjectId(GOOGLE_CLOUD_GREENBIDS_PROJECT).build().getService();
    }

    public OnnxModelRunner getModelRunner() {
        Blob blob = getBlob();
        Instant currentLastModifiedTime = Instant.ofEpochMilli(blob.getUpdateTime());

        System.out.println(
                "getModelRunner: \n" +
                        "blob: " + blob + "\n" +
                        "currentLastModifiedTime: " + currentLastModifiedTime + "\n" +
                        "lastModifiedTime: " + lastModifiedTime + "\n" +
                        "cache: " + cache
        );

        if (!lastModifiedTime.equals(currentLastModifiedTime)) {
            cache.invalidateAll();
            lastModifiedTime = currentLastModifiedTime;
        }

        return cache.get("onnxModelRunner", key -> loadModelRunner(blob));
    }

    private Blob getBlob() {
        System.out.println(
                "getBlob: \n" +
                        "storage: " + storage + "\n" +
                        "GCS_BUCKET_NAME: " + GCS_BUCKET_NAME + "\n" +
                        "modelPath: " + modelPath + "\n"
        );
        return storage.get(GCS_BUCKET_NAME).get(modelPath);
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
