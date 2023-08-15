package org.prebid.server.activity.infrastructure.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.List;

public class ActivityDebugUtils {

    private ActivityDebugUtils() {
    }

    public static JsonNode asLogEntry(Object object, ObjectMapper mapper) {
        return object instanceof Loggable loggable
                ? loggable.asLogEntry(mapper)
                : TextNode.valueOf(object.toString());
    }

    public static ArrayNode asLogEntry(List<?> objects, ObjectMapper mapper) {
        final ArrayNode arrayNode = mapper.createArrayNode();
        objects.stream()
                .map(object -> asLogEntry(object, mapper))
                .forEach(arrayNode::add);

        return arrayNode;
    }
}
