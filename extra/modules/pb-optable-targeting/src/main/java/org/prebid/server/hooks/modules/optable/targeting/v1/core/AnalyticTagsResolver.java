package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Reason;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AnalyticTagsResolver {

    private static final String ACTIVITY_ENRICH_REQUEST = "optable-enrich-request";
    private static final String ACTIVITY_ENRICH_RESPONSE = "optable-enrich-response";
    private static final String STATUS_EXECUTION_TIME = "execution-time";
    private static final String STATUS_REASON = "reason";

    private AnalyticTagsResolver() {
    }

    public static Tags toEnrichRequestAnalyticTags(ModuleContext moduleContext) {
        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                ACTIVITY_ENRICH_REQUEST,
                toEnrichmentStatusValue(moduleContext.getEnrichRequestStatus()),
                toResults(STATUS_EXECUTION_TIME, String.valueOf(moduleContext.getOptableTargetingExecutionTime())))));
    }

    public static Tags toEnrichResponseAnalyticTags(ModuleContext moduleContext) {
        final List<Activity> activities = new ArrayList<>();
        if (moduleContext.isAdserverTargetingEnabled()) {
            activities.add(ActivityImpl.of(
                    ACTIVITY_ENRICH_RESPONSE,
                    toEnrichmentStatusValue(moduleContext.getEnrichResponseStatus()),
                    toResults(STATUS_REASON, toEnrichmentStatusReason(moduleContext.getEnrichResponseStatus()))));
        }

        return TagsImpl.of(Collections.unmodifiableList(activities));
    }

    private static String toEnrichmentStatusValue(EnrichmentStatus enrichRequestStatus) {
        return Optional.ofNullable(enrichRequestStatus)
                .map(EnrichmentStatus::getStatus)
                .map(Status::getValue)
                .orElse(null);
    }

    private static String toEnrichmentStatusReason(EnrichmentStatus enrichmentStatus) {
        return Optional.ofNullable(enrichmentStatus)
                .map(EnrichmentStatus::getReason)
                .map(Reason::getValue)
                .orElse(null);
    }

    private static List<Result> toResults(String result, String value) {
        final ObjectNode resultDetails = ObjectMapperProvider.mapper().createObjectNode().put(result, value);
        return Collections.singletonList(ResultImpl.of(null, resultDetails, null));
    }
}
