package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Storage;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ThresholdCache;

import java.util.Comparator;
import java.util.List;

@Builder(toBuilder = true)
@Value
public class Partner {

    String GCS_BUCKET_NAME = "greenbids-europe-west1-prebid-server-staging";

    @JsonProperty("pbuid")
    String pbuid;

    @JsonProperty("targetTpr")
    Double targetTpr;

    @JsonProperty("explorationRate")
    Double explorationRate;

    @JsonCreator
    public Partner(
            @JsonProperty("pbuid") String pbuid,
            @JsonProperty("targetTpr") Double targetTpr,
            @JsonProperty("explorationRate") Double explorationRate) {
        this.pbuid = pbuid;
        this.targetTpr = targetTpr;
        this.explorationRate = explorationRate;
    }

    public Double getThresholdForPartner(Storage storage, ObjectMapper mapper) {
        String thresholdJsonPath = "thresholds_pbuid=" + pbuid + ".json";
        ThresholdCache thresholdCache = new ThresholdCache(
                thresholdJsonPath,
                storage,
                GCS_BUCKET_NAME,
                mapper);
        ThrottlingThresholds throttlingThresholds = thresholdCache.getThrottlingThresholds();

        List<Double> truePositiveRates = throttlingThresholds.getTpr();
        List<Double> thresholds = throttlingThresholds.getThresholds();

        return truePositiveRates.stream()
                .filter(truePositiveRate -> truePositiveRate >= targetTpr)
                .map(truePositiveRate -> thresholds.get(truePositiveRates.indexOf(truePositiveRate)))
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }

    public OnnxModelRunner getOnnxModelRunner(Storage storage) {
        String onnxModelPath = "models_pbuid=" + pbuid + ".onnx";
        ModelCache modelCache = new ModelCache(onnxModelPath, storage, GCS_BUCKET_NAME);
        return modelCache.getModelRunner();
    }
}
