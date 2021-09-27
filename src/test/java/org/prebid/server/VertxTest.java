package org.prebid.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import java.io.IOException;

public abstract class VertxTest {

    protected static ObjectMapper mapper;
    protected static JacksonMapper jacksonMapper;

    static {
        mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);
    }

    protected static String jsonFrom(String file) throws IOException {
        return mapper.writeValueAsString(mapper.readTree(VertxTest.class.getResourceAsStream(file)));
    }

    protected static JsonNode jsonNodeFrom(String file) throws IOException {
        return mapper.readTree(VertxTest.class.getResourceAsStream(file));
    }
}
