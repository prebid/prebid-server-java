package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Storage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;

public class OnnxModelRunnerWithThresholds {

    private final Cache<String, OnnxModelRunner> modelCacheWithExpiration;
    private final Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;
    private final String gcsBucketName;
    private final String onnxModelCacheKeyPrefix;
    private final String thresholdsCacheKeyPrefix;
    private final Storage storage;
    private final Vertx vertx;

    public OnnxModelRunnerWithThresholds(
            Cache<String, OnnxModelRunner> modelCacheWithExpiration,
            Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration,
            Storage storage,
            String gcsBucketName,
            String onnxModelCacheKeyPrefix,
            String thresholdsCacheKeyPrefix,
            Vertx vertx) {
        this.modelCacheWithExpiration = modelCacheWithExpiration;
        this.thresholdsCacheWithExpiration = thresholdsCacheWithExpiration;
        this.gcsBucketName = gcsBucketName;
        this.onnxModelCacheKeyPrefix = onnxModelCacheKeyPrefix;
        this.thresholdsCacheKeyPrefix = thresholdsCacheKeyPrefix;
        this.storage = storage;
        this.vertx = vertx;
    }

    public Future<OnnxModelRunner> retrieveOnnxModelRunner(Partner partner) {
        final String onnxModelPath = "models_pbuid=" + partner.getPbuid() + ".onnx";
        final ModelCache modelCache = new ModelCache(
                onnxModelPath,
                storage,
                gcsBucketName,
                modelCacheWithExpiration,
                onnxModelCacheKeyPrefix,
                vertx);
        return modelCache.getModelRunner(partner.getPbuid());
    }

    public Future<Double> retrieveThreshold(Partner partner, ObjectMapper mapper) {
        final String thresholdJsonPath = "thresholds_pbuid=" + partner.getPbuid() + ".json";
        final ThresholdCache thresholdCache = new ThresholdCache(
                thresholdJsonPath,
                storage,
                gcsBucketName,
                mapper,
                thresholdsCacheWithExpiration,
                thresholdsCacheKeyPrefix,
                vertx);
        return thresholdCache.getThrottlingThresholds(partner.getPbuid())
                .map(partner::getThreshold);
    }
}
