package org.prebid.server.hooks.modules.optable.targeting.v1.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.v1.analytics.Activity;
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
        return ActivityImpl.of(ACTIVITY_ENRICH_REQUEST,
                requestEnrichmentStatus != null
                        ? requestEnrichmentStatus.getValue()
                        : null,
                List.of(ResultImpl.of(null, objectMapper.createObjectNode().put(STATUS_EXECUTION_TIME,
                                requestEnrichmentExecutionTime), null)));
    }

    private Activity buildEnrichResponseActivity() {
        return ActivityImpl.of(ACTIVITY_ENRICH_RESPONSE,
                responseEnrichmentStatus != null
                        ? responseEnrichmentStatus.status().getValue()
                        : null,
                List.of(ResultImpl.of(null, objectMapper.createObjectNode().put(STATUS_REASON,
                        responseEnrichmentStatus != null
                                ? responseEnrichmentStatus.reason().getValue()
                                : null), null)));
    }
}
