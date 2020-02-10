package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@SuppressWarnings("serial")
class TimeoutModule extends SimpleModule {

    TimeoutModule() {
        addDeserializer(Timeout.class, new TimeoutDeserializer());
    }

    private static class TimeoutDeserializer extends JsonDeserializer<Timeout> {
        @Autowired
        TimeoutFactory timeoutFactory;

        @Override
        public Timeout deserialize(JsonParser jsonParser,
                                   DeserializationContext deserializationContext) throws IOException {
            return timeoutFactory.create(jsonParser.getValueAsLong());
        }

    }
}
