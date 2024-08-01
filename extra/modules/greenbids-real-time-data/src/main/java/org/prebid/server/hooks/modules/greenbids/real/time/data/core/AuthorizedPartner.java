package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import lombok.Getter;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ModelCache;

import java.util.Arrays;

public enum AuthorizedPartner {
    TEST(Partner.builder()
            .pbuid("PBUID_FROM_GREENBIDS")
            .targetTpr(0.95)
            .explorationRate(0.001)
            .thresholdsJsonPath("thresholds_pbuid=PBUID_FROM_GREENBIDS.json")
            .build(),
            new ModelCache("models_pbuid=PBUID_FROM_GREENBIDS.onnx")),

    LELP(Partner.builder()
            .pbuid("lelp-pbuid")
            .targetTpr(0.99)
            .explorationRate(0.2)
            .thresholdsJsonPath("thresholds_pbuid=lelp-pbuid.json")
            .build(),
            new ModelCache("models_pbuid=lelp-pbuid.onnx"));

    @Getter
    private final Partner partner;

    AuthorizedPartner(Partner partner, ModelCache modelCache) {
        this.partner = partner.toBuilder()
                .onnxModelRunner(modelCache.getModelRunner())
                .build();
        System.out.println("AuthorizedPartner partner: " + this.partner);
    }

    public static Partner getPartnerByPbuid(String pbuid) {
        return Arrays.stream(values())
                .map(AuthorizedPartner::getPartner)
                .filter(partner -> partner.getPbuid().equals(pbuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No partner found with pbuid: " + pbuid));
    }
}
