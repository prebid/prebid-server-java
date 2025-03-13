package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Metrics(long moduleStartTime, long moduleFinishTime) {
}
