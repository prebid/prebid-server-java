package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Storage;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;

public class OnnxModelRunnerWithThresholds {

    private final ModelCache modelCache;
    private final ThresholdCache thresholdCache;

    public OnnxModelRunnerWithThresholds(
            ModelCache modelCache,
            ThresholdCache thresholdCache) {
        this.modelCache = modelCache;
        this.thresholdCache = thresholdCache;
    }

    public Future<OnnxModelRunner> retrieveOnnxModelRunner(Partner partner) {
        final String onnxModelPath = "models_pbuid=" + partner.getPbuid() + ".onnx";
        return modelCache.getModelRunner(onnxModelPath, partner.getPbuid());
    }

    public Future<Double> retrieveThreshold(Partner partner) {
        final String thresholdJsonPath = "thresholds_pbuid=" + partner.getPbuid() + ".json";
        return thresholdCache.getThrottlingThresholds(thresholdJsonPath, partner.getPbuid())
                .map(partner::getThreshold);
    }
}
