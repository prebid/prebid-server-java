package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

class ZonedDateTimeModule extends SimpleModule {

    // see https://stackoverflow.com/q/30090710
    // this format is equal to "yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnnXXX" but allows less than 9 nanosecond digits in
    // parsed strings, as a side effect trailing zeros will be removed when formatting ZonedDateTime into string
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendPattern("XXX")
            .toFormatter();

    ZonedDateTimeModule() {
        addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer());
        addDeserializer(ZonedDateTime.class, new ZonedDateTimeDeserializer());
    }

    private static class ZonedDateTimeSerializer extends JsonSerializer<ZonedDateTime> {

        @Override
        public void serialize(ZonedDateTime value, JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            generator.writeString(FORMATTER.format(value));
        }
    }

    private static class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

        @Override
        public ZonedDateTime deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            return ZonedDateTime.parse(parser.getValueAsString(), FORMATTER);
        }
    }
}
