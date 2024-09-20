package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModelCache {

    private static final Logger logger = LoggerFactory.getLogger(ModelCache.class);

    String gcsBucketName;

    Cache<String, OnnxModelRunner> cache;

    Storage storage;

    String onnxModelCacheKeyPrefix;

    AtomicBoolean isFetching;

    Vertx vertx;

    public ModelCache(
            Storage storage,
            String gcsBucketName,
            Cache<String, OnnxModelRunner> cache,
            String onnxModelCacheKeyPrefix,
            Vertx vertx) {
        this.gcsBucketName = Objects.requireNonNull(gcsBucketName);
        this.cache = Objects.requireNonNull(cache);
        this.storage = Objects.requireNonNull(storage);
        this.onnxModelCacheKeyPrefix = Objects.requireNonNull(onnxModelCacheKeyPrefix);
        this.isFetching = new AtomicBoolean(false);
        this.vertx = Objects.requireNonNull(vertx);
    }

    public Future<OnnxModelRunner> get(String onnxModelPath, String pbuid) {
        final String cacheKey = onnxModelCacheKeyPrefix + pbuid;
        final OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);

        if (cachedOnnxModelRunner != null) {
            return Future.succeededFuture(cachedOnnxModelRunner);
        }

        if (isFetching.compareAndSet(false, true)) {
            try {
                fetchAndCacheModelRunner(onnxModelPath, cacheKey);
            } finally {
                isFetching.set(false);
            }
        }

        return Future.failedFuture("ModelRunner fetching in progress");
    }

    private void fetchAndCacheModelRunner(String onnxModelPath, String cacheKey) {
        vertx.executeBlocking(() -> getBlob(onnxModelPath))
                .map(this::loadModelRunner)
                .onSuccess(onnxModelRunner -> cache.put(cacheKey, onnxModelRunner))
                .onFailure(error -> logger.error("Failed to fetch ONNX model"));
    }

    private Blob getBlob(String onnxModelPath) {
        try {
            return Optional.ofNullable(storage.get(gcsBucketName))
                    .map(bucket -> bucket.get(onnxModelPath))
                    .orElseThrow(() -> new PreBidException("Bucket not found: " + gcsBucketName));
        } catch (StorageException e) {
            throw new PreBidException("Error accessing GCS artefact for model: ", e);
        }
    }

    private OnnxModelRunner loadModelRunner(Blob blob) {
        try {
            final byte[] onnxModelBytes = blob.getContent();
            return new OnnxModelRunner(onnxModelBytes);
        } catch (OrtException e) {
            throw new PreBidException("Failed to convert blob to ONNX model", e);
        }
    }
}
