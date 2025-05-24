package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class EnrichmentStatus {

    Status status;

    Reason reason;

    public static EnrichmentStatus failure() {
        return EnrichmentStatus.of(Status.FAIL, null);
    }

    public static EnrichmentStatus success() {
        return EnrichmentStatus.of(Status.SUCCESS, null);
    }
}
