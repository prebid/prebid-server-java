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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModelCache {

    private static final Logger logger = LoggerFactory.getLogger(ModelCache.class);

    String gcsBucketName;

    String modelPath;

    Cache<String, OnnxModelRunner> cache;

    Storage storage;

    String onnxModelCacheKeyPrefix;

    AtomicBoolean isFetching;

    Vertx vertx;

    public ModelCache(
            String modelPath,
            Storage storage,
            String gcsBucketName,
            Cache<String, OnnxModelRunner> cache,
            String onnxModelCacheKeyPrefix,
            Vertx vertx) {
        this.gcsBucketName = gcsBucketName;
        this.modelPath = modelPath;
        this.cache = cache;
        this.storage = storage;
        this.onnxModelCacheKeyPrefix = onnxModelCacheKeyPrefix;
        this.isFetching = new AtomicBoolean(false);
        this.vertx = vertx;
    }

    public Future<OnnxModelRunner> getModelRunner(String pbuid) {
        final String cacheKey = onnxModelCacheKeyPrefix + pbuid;
        final OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);

        if (cachedOnnxModelRunner != null) {
            return Future.succeededFuture(cachedOnnxModelRunner);
        }

        if (isFetching.compareAndSet(false, true)) {
            try {
                fetchAndCacheModelRunner(cacheKey);
            } finally {
                isFetching.set(false);
            }
        }

        return Future.failedFuture("ModelRunner fetching in progress");
    }

    private void fetchAndCacheModelRunner(String cacheKey) {
        vertx.executeBlocking(this::getBlob)
                .map(this::loadModelRunner)
                .onSuccess(onnxModelRunner -> cache.put(cacheKey, onnxModelRunner))
                .onFailure(error -> logger.error("Failed to fetch ONNX model"));
    }

    private Blob getBlob() {
        try {
            return Optional.ofNullable(storage.get(gcsBucketName))
                    .map(bucket -> bucket.get(modelPath))
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
