package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Json deserializer to convert array of string to comma separated array elements.
 */
public class CommaSeparatedStringArrayDeserializer extends StdDeserializer<String> {

    public CommaSeparatedStringArrayDeserializer() {
        this(null);
    }

    public CommaSeparatedStringArrayDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        final JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        }

        if (node.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) node;
            if (!arrayNode.isEmpty() && isTextualArray(arrayNode)) {
                return StreamSupport.stream(arrayNode.spliterator(), false)
                        .map(jsonNode -> (TextNode) jsonNode)
                        .map(TextNode::textValue)
                        .collect(Collectors.joining(","));
            }
        }
        return null;
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .anyMatch(jsonNode -> !jsonNode.isTextual());
    }
}
