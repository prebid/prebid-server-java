package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.util.concurrent.atomic.LongAdder;

class LongAdderModule extends SimpleModule {

    LongAdderModule() {
        addSerializer(LongAdder.class, new LongAdderSerializer());
        addDeserializer(LongAdder.class, new LongAdderDeserializer());
    }

    private static class LongAdderSerializer extends JsonSerializer<LongAdder> {

        @Override
        public void serialize(LongAdder value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeNumber(value.longValue());
        }
    }

    private static class LongAdderDeserializer extends JsonDeserializer<LongAdder> {

        @Override
        public LongAdder deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            final LongAdder result = new LongAdder();
            result.add(parser.getLongValue());
            return result;
        }
    }
}
