package org.prebid.server.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Deserializes JsonNode to set null instead of NullNode for missing fields, that have JsonNode type in Java POJO.
 */
public class MissingJsonNodeModule extends SimpleModule {

    MissingJsonNodeModule() {
        addDeserializer(JsonNode.class, new JsonNodeDeserializer());
    }

    private static class JsonNodeDeserializer extends com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer {

        @Override
        public JsonNode getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
