package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record EnrichmentStatus(Status status, Reason reason) {

    public static EnrichmentStatus fail() {
        return EnrichmentStatus.builder().status(Status.FAIL).build();
    }

    public static EnrichmentStatus success() {
        return EnrichmentStatus.builder().status(Status.SUCCESS).build();
    }
}
