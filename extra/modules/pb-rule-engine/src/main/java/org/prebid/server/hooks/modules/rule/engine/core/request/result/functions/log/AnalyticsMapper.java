package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.log;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.InfrastructureArguments;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.Collections;
import java.util.List;

public class AnalyticsMapper {

    private static final String ACTIVITY_NAME = "pb-rule-engine";
    private static final String SUCCESS_STATUS = "success";

    private AnalyticsMapper() {
    }

    public static Tags toTags(ObjectMapper mapper,
                              InfrastructureArguments<RequestRuleContext> infrastructureArguments,
                              String analyticsValue) {

        final String analyticsKey = infrastructureArguments.getAnalyticsKey();
        if (StringUtils.isEmpty(analyticsKey)) {
            return TagsImpl.of(Collections.emptyList());
        }

        final AnalyticsData analyticsData = new AnalyticsData(
                analyticsKey,
                analyticsValue,
                infrastructureArguments.getModelVersion(),
                infrastructureArguments.getRuleFired(),
                LogATagFunction.NAME);

        final Granularity granularity = infrastructureArguments.getContext().getGranularity();
        final List<String> impIds = granularity instanceof Granularity.Imp(String impId)
                ? Collections.singletonList(impId)
                : Collections.singletonList("*");

        final Result result = ResultImpl.of(
                SUCCESS_STATUS, mapper.valueToTree(analyticsData), AppliedToImpl.builder().impIds(impIds).build());

        return TagsImpl.of(Collections.singletonList(
                ActivityImpl.of(ACTIVITY_NAME, SUCCESS_STATUS, Collections.singletonList(result))));
    }

    private record AnalyticsData(@JsonProperty("analyticsKey") String analyticsKey,
                                 @JsonProperty("analyticsValue") String analyticsValue,
                                 @JsonProperty("modelVersion") String modelVersion,
                                 @JsonProperty("conditionFired") String conditionFired,
                                 @JsonProperty("resultFunction") String resultFunction) {
    }
}
