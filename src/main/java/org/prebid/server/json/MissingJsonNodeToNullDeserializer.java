package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class MissingJsonNodeToNullDeserializer extends StdDeserializer<JsonNode> {

    public MissingJsonNodeToNullDeserializer() {
        this(null);
    }

    public MissingJsonNodeToNullDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public JsonNode deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        final JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        return node;
    }
}
