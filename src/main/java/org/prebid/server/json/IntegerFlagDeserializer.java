package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Deserialized json boolean FALSE to 0 and TRUE to 1.
 */
public class IntegerFlagDeserializer extends StdDeserializer<Integer> {

    public IntegerFlagDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        switch (parser.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                return parser.getValueAsInt();
            case VALUE_FALSE:
                return 0;
            case VALUE_TRUE:
                return 1;
            default:
                ctxt.reportWrongTokenException(JsonToken.class, JsonToken.VALUE_NUMBER_INT,
                        String.format("Failed to parse field %s to Integer type with a reason: Expected type boolean"
                                + " or integer(`0` or `1`).", parser.getCurrentName()));
                // the previous method should have thrown
                throw new AssertionError();
        }
    }
}
