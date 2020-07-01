package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.stream.StreamSupport;

/**
 * Json deserializer to convert array of string to first element of array.
 */
public class StringArrayToFirstItemDeserializer extends StdDeserializer<String> {

    public StringArrayToFirstItemDeserializer() {
        this(null);
    }

    public StringArrayToFirstItemDeserializer(Class<?> vc) {
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
                return arrayNode.get(0).asText();
            }
        }
        return null;
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .anyMatch(jsonNode -> !jsonNode.isTextual());
    }
}
