package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import io.vertx.core.Future;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsConfig;

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

    public Future<OnnxModelRunner> retrieveOnnxModelRunner(GreenbidsConfig greenbidsConfig) {
        final String onnxModelPath = "models_pbuid=" + greenbidsConfig.getPbuid() + ".onnx";
        return modelCache.get(onnxModelPath, greenbidsConfig.getPbuid());
    }

    public Future<Double> retrieveThreshold(GreenbidsConfig greenbidsConfig) {
        final String thresholdJsonPath = "thresholds_pbuid=" + greenbidsConfig.getPbuid() + ".json";
        return thresholdCache.get(thresholdJsonPath, greenbidsConfig.getPbuid())
                .map(greenbidsConfig::getThreshold);
    }
}
