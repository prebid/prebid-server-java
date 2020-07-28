package org.prebid.server.validation.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Value;

import java.io.IOException;

@Value(staticConstructor = "of")
@JsonDeserialize(using = Size.SizeDeserializer.class)
public class Size {

    Integer width;

    Integer height;

    public static class SizeDeserializer extends JsonDeserializer<Size> {

        @Override
        public Size deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            final String sizeAsString = parser.getValueAsString();

            final String[] widthAndHeight = sizeAsString.split("x");
            if (widthAndHeight.length != 2) {
                throw new JsonMappingException(
                        parser,
                        String.format("Invalid size format: %s. Should be '[width]x[height]'", sizeAsString));
            }

            try {
                return Size.of(
                        Integer.parseInt(widthAndHeight[0]),
                        Integer.parseInt(widthAndHeight[1]));
            } catch (NumberFormatException e) {
                throw new JsonMappingException(parser, "Invalid size format", e);
            }
        }
    }
}
