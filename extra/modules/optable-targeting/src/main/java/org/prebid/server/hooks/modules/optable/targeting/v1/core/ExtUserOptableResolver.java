package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.ExtUserOptable;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;

public class ExtUserOptableResolver {

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(ExtUserOptableResolver.class));

    private ExtUserOptableResolver() {
    }

    public static ExtUserOptable resolveExtUserOptable(JsonNode node, double logSamplingRate) {
        try {
            return ObjectMapperProvider.mapper().treeToValue(node, ExtUserOptable.class);
        } catch (JsonProcessingException e) {
            conditionalLogger.warn("Can't parse $.ext.user.Optable tag", logSamplingRate);
            return null;
        }
    }
}
