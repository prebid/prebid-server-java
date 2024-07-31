package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import lombok.Getter;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ModelCache;

import java.util.Arrays;

public enum AuthorizedPartner {
    TEST(Partner.builder()
            .pbuid("PBUID_FROM_GREENBIDS")
            .targetTpr(0.95)
            .explorationRate(0.001)
            //.onnxModelRunner(PredefinedModelRunners.testModelRunner)
            .thresholdsJsonPath("extra/modules/greenbids-real-time-data/src/main/resources/thresholds_pbuid=PBUID_FROM_GREENBIDS.json")
            .build(),
            new ModelCache("extra/modules/greenbids-real-time-data/src/main/resources/models_pbuid=PBUID_FROM_GREENBIDS.onnx")),

    LELP(Partner.builder()
            .pbuid("lelp-pbuid")
            .targetTpr(0.99)
            .explorationRate(0.2)
            //.onnxModelRunner(PredefinedModelRunners.lelpModelRunner)
            .thresholdsJsonPath("extra/modules/greenbids-real-time-data/src/main/resources/thresholds_pbuid=lelp-pbuid.json")
            .build(),
            new ModelCache("extra/modules/greenbids-real-time-data/src/main/resources/models_pbuid=lelp-pbuid.onnx"));

    @Getter
    private final Partner partner;

    private final ModelCache modelCache;

    AuthorizedPartner(Partner partner, ModelCache modelCache) {
        this.partner = partner.toBuilder()
                .onnxModelRunner(modelCache.getModelRunner())
                .build();
        this.modelCache = modelCache;

        //partner = partner.toBuilder()
        //        .onnxModelRunner(modelCache.getModelRunner())
        //        .build();

        //try {
        //    partner = partner.toBuilder()
        //            .onnxModelRunner(modelCache.getModelRunner())
        //            .build();
        //} catch (OrtException e) {
        //    throw new RuntimeException("Failed to initialize ONNX Model Runner", e);
        //}
    }

    public static Partner getPartnerByPbuid(String pbuid) {
        return Arrays.stream(values())
                .map(AuthorizedPartner::getPartner)
                .filter(partner -> partner.getPbuid().equals(pbuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No partner found with pbuid: " + pbuid));
    }
}
