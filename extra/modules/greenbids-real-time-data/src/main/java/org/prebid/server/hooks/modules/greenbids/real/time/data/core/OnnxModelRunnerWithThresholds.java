package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import io.vertx.core.Future;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.Partner;

import java.util.Objects;

public class OnnxModelRunnerWithThresholds {

    private final ModelCache modelCache;

    private final ThresholdCache thresholdCache;

    public OnnxModelRunnerWithThresholds(
            ModelCache modelCache,
            ThresholdCache thresholdCache) {
        this.modelCache = Objects.requireNonNull(modelCache);
        this.thresholdCache = Objects.requireNonNull(thresholdCache);
    }

    public Future<OnnxModelRunner> retrieveOnnxModelRunner(Partner partner) {
        final String onnxModelPath = "models_pbuid=" + partner.getPbuid() + ".onnx";
        return modelCache.get(onnxModelPath, partner.getPbuid());
    }

    public Future<Double> retrieveThreshold(Partner partner) {
        final String thresholdJsonPath = "thresholds_pbuid=" + partner.getPbuid() + ".json";
        return thresholdCache.get(thresholdJsonPath, partner.getPbuid())
                .map(partner::getThreshold);
    }
}
