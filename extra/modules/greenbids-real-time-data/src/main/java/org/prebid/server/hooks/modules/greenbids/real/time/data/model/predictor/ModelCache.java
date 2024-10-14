package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OrtException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModelCache {

    private static final Logger logger = LoggerFactory.getLogger(ModelCache.class);

    private final String gcsBucketName;

    private final Cache<String, OnnxModelRunner> cache;

    private final Storage storage;

    private final String onnxModelCacheKeyPrefix;

    private final AtomicBoolean isFetching;

    private final Vertx vertx;

    private final OnnxModelRunnerFactory onnxModelRunnerFactory;

    public ModelCache(
            Storage storage,
            String gcsBucketName,
            Cache<String, OnnxModelRunner> cache,
            String onnxModelCacheKeyPrefix,
            Vertx vertx,
            OnnxModelRunnerFactory onnxModelRunnerFactory) {
        this.gcsBucketName = Objects.requireNonNull(gcsBucketName);
        this.cache = Objects.requireNonNull(cache);
        this.storage = Objects.requireNonNull(storage);
        this.onnxModelCacheKeyPrefix = Objects.requireNonNull(onnxModelCacheKeyPrefix);
        this.isFetching = new AtomicBoolean(false);
        this.vertx = Objects.requireNonNull(vertx);
        this.onnxModelRunnerFactory = Objects.requireNonNull(onnxModelRunnerFactory);
    }

    public Future<OnnxModelRunner> get(String onnxModelPath, String pbuid) {
        final String cacheKey = onnxModelCacheKeyPrefix + pbuid;
        final OnnxModelRunner cachedOnnxModelRunner = cache.getIfPresent(cacheKey);

        if (cachedOnnxModelRunner != null) {
            return Future.succeededFuture(cachedOnnxModelRunner);
        }

        System.out.println(
                "get: " + "\n" +
                        "   isFetching: " + isFetching
        );

        if (isFetching.compareAndSet(false, true)) {
            try {
                return fetchAndCacheModelRunner(onnxModelPath, cacheKey);
            } finally {
                isFetching.set(false);
            }
        }

        return Future.failedFuture("ModelRunner fetching in progress. Skip current request");
    }

    private Future<OnnxModelRunner>  fetchAndCacheModelRunner(String onnxModelPath, String cacheKey) {
        System.out.println(
                "fetchAndCacheModelRunner: \n" +
                        "   onnxModelPath: " + onnxModelPath + "\n" +
                        "   cacheKey: " + cacheKey
        );

        return vertx.executeBlocking(() -> getBlob(onnxModelPath))
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
            System.out.println("StorageException trigger PreBidException");
            throw new PreBidException("Error accessing GCS artefact for model: ", e);
        }
    }

    private OnnxModelRunner loadModelRunner(Blob blob) {
        try {
            final byte[] onnxModelBytes = blob.getContent();

            System.out.println(
                    "loadModelRunner: \n" +
                            "   blob: " + blob + "\n" +
                            "   onnxModelBytes: " + Arrays.toString(onnxModelBytes) + "\n"
            );

            return onnxModelRunnerFactory.create(onnxModelBytes);
        } catch (OrtException e) {
            System.out.println("OrtException trigger PreBidException");
            throw new PreBidException("Failed to convert blob to ONNX model", e);
        }
    }
}
