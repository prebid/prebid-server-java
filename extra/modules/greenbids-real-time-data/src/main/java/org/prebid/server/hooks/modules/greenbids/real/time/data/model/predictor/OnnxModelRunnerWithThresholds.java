package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Storage;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.json.JacksonMapper;

public class OnnxModelRunnerWithThresholds {

    JacksonMapper jacksonMapper;
    Cache<String, OnnxModelRunner> modelCacheWithExpiration;
    Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;

    private final String gcsBucketName;
    private final String onnxModelCacheKeyPrefix;
    private final String thresholdsCacheKeyPrefix;
    private final Storage storage;

    public OnnxModelRunnerWithThresholds(
            JacksonMapper jacksonMapper,
            Cache<String, OnnxModelRunner> modelCacheWithExpiration,
            Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration,
            Storage storage,
            String gcsBucketName,
            String onnxModelCacheKeyPrefix,
            String thresholdsCacheKeyPrefix) {
        this.modelCacheWithExpiration = modelCacheWithExpiration;
        this.thresholdsCacheWithExpiration = thresholdsCacheWithExpiration;
        this.jacksonMapper = jacksonMapper;
        this.gcsBucketName = gcsBucketName;
        this.onnxModelCacheKeyPrefix = onnxModelCacheKeyPrefix;
        this.thresholdsCacheKeyPrefix = thresholdsCacheKeyPrefix;
        this.storage = storage;
    }

    public OnnxModelRunner retrieveOnnxModelRunner(Partner partner) {
        final String onnxModelPath = "models_pbuid=" + partner.getPbuid() + ".onnx";
        final ModelCache modelCache = new ModelCache(
                onnxModelPath,
                storage,
                gcsBucketName,
                modelCacheWithExpiration,
                onnxModelCacheKeyPrefix);
        return modelCache.getModelRunner(partner.getPbuid());
    }

    public ThrottlingThresholds retrieveThreshold(Partner partner) {
        final String thresholdJsonPath = "thresholds_pbuid=" + partner.getPbuid() + ".json";
        final ThresholdCache thresholdCache = new ThresholdCache(
                thresholdJsonPath,
                storage,
                gcsBucketName,
                jacksonMapper.mapper(),
                thresholdsCacheWithExpiration,
                thresholdsCacheKeyPrefix);
        return thresholdCache.getThrottlingThresholds(partner.getPbuid());
    }
}
