package org.prebid.server.hooks.modules.optable.targeting.v1.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.ArrayList;
import java.util.List;

@Builder(toBuilder = true)
public class AnalyticsTagsBuilder {

    private static final String STATUS_OK = "OK";

    private static final String ACTIVITY_ENRICH_REQUEST = "optable-enrich-request";

    private static final String ACTIVITY_ENRICH_RESPONSE = "optable-enrich-response";

    private static final String STATUS_EXECUTION_TIME = "execution-time";

    private static final String STATUS_REASON = "reason";

    private final ObjectMapper objectMapper;

    private final long requestEnrichmentExecutionTime;

    private final Status requestEnrichmentStatus;

    private final EnrichmentStatus responseEnrichmentStatus;

    private final boolean adserverTargeting;

    public Tags buildTags() {
        final List<Activity> activities = new ArrayList<>();
        activities.add(buildEnrichRequestActivity());

        if (adserverTargeting) {
            activities.add(buildEnrichResponseActivity());
        }
        return TagsImpl.of(activities);
    }

    private Activity buildEnrichRequestActivity() {
        final String activityStatus = requestEnrichmentStatus != null ? requestEnrichmentStatus.getValue() : null;
        final Result activityResult = buildResult(STATUS_EXECUTION_TIME, requestEnrichmentExecutionTime);

        return ActivityImpl.of(
                ACTIVITY_ENRICH_REQUEST,
                activityStatus,
                List.of(activityResult));
    }

    private Activity buildEnrichResponseActivity() {
        final String activityStatus = requestEnrichmentStatus != null ? requestEnrichmentStatus.getValue() : null;
        final String enrichmentStatus = responseEnrichmentStatus != null
                ? responseEnrichmentStatus.reason().getValue()
                : null;
        final Result activityResult = buildResult(STATUS_REASON, enrichmentStatus);

        return ActivityImpl.of(
                ACTIVITY_ENRICH_RESPONSE,
                activityStatus,
                List.of(activityResult));
    }

    private Result buildResult(String resultName, String resultValue) {
        final ObjectNode resultObj = objectMapper.createObjectNode()
                .put(resultName, resultValue);

        return ResultImpl.of(null, resultObj, null);
    }

    private Result buildResult(String resultName, Long resultValue) {
        final ObjectNode resultObj = objectMapper.createObjectNode()
                .put(resultName, resultValue);

        return ResultImpl.of(null, resultObj, null);
    }
}
