package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CommaSeparatedStringAsListOfIntegersDeserializer extends StdDeserializer<List<Integer>> {

    public CommaSeparatedStringAsListOfIntegersDeserializer() {
        super(List.class);
    }

    @Override
    public List<Integer> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.getCurrentToken() != JsonToken.VALUE_STRING) {
            reportWrongTokenException(parser, context);
        }

        final String value = parser.getValueAsString();
        if (value == null) {
            reportWrongTokenException(parser, context);
        }

        try {
            return parseList(value);
        } catch (NumberFormatException e) {
            reportPropertyInputMismatch(parser, context, e.getMessage());
            throw new AssertionError();
        }
    }

    private static void reportWrongTokenException(JsonParser parser, DeserializationContext context)
            throws IOException {

        context.reportWrongTokenException(
                JsonToken.class,
                JsonToken.VALUE_STRING,
                """
                        Failed to parse field %s to List<Integer> type with a reason: \
                        Expected comma-separated string.""".formatted(parser.getCurrentName()));
    }

    private static List<Integer> parseList(String value) throws NumberFormatException {
        return Arrays.stream(value.split(","))
                .map(StringUtils::strip)
                .filter(StringUtils::isNotBlank)
                .map(Integer::parseInt)
                .toList();
    }

    private static void reportPropertyInputMismatch(JsonParser parser, DeserializationContext context, String cause)
            throws IOException {

        context.reportPropertyInputMismatch(
                JsonToken.class,
                parser.getCurrentName(),
                """
                        Failed to parse field %s to List<Integer> type with a reason: \
                        NumberFormatException %s""".formatted(parser.getCurrentName(), cause));
    }
}
