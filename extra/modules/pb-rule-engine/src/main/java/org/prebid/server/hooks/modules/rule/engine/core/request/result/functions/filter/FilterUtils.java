package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.stream.Stream;

public class FilterUtils {

    private static final String PREBID = "prebid";
    private static final String BIDDER = "bidder";

    public FilterUtils() {
    }

    public static ObjectNode bidderNode(ObjectNode impExt) {
        return Optional.ofNullable(impExt.get(PREBID))
                .filter(JsonNode::isObject)
                .map(prebidNode -> (ObjectNode) prebidNode)
                .map(prebidNode -> prebidNode.get(BIDDER))
                .filter(JsonNode::isObject)
                .map(bidderNode -> (ObjectNode) bidderNode)
                .orElseThrow(() -> new IllegalStateException("Impression without ext.prebid.bidder"));
    }

    public static boolean containsIgnoreCase(Stream<String> stream, String value) {
        return stream.anyMatch(value::equalsIgnoreCase);
    }
}
