package org.prebid.server.hooks.modules.optable.targeting.v1.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.Objects;

public class AnalyticTagsResolver {

    private final ObjectMapper objectMapper;

    public AnalyticTagsResolver(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public Tags resolve(ModuleContext moduleContext) {
        return AnalyticsTagsBuilder.builder()
                .objectMapper(objectMapper)
                .adserverTargeting(moduleContext.isAdserverTargetingEnabled())
                .requestEnrichmentStatus(moduleContext.getEnrichRequestStatus().getStatus())
                .responseEnrichmentStatus(moduleContext.getEnrichResponseStatus())
                .requestEnrichmentExecutionTime(moduleContext.getOptableTargetingExecutionTime())
                .build()
                .buildTags();
    }
}
