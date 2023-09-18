package org.prebid.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

public abstract class VertxTest {

    protected static ObjectMapper mapper;
    protected static JacksonMapper jacksonMapper;

    static {
        mapper = ObjectMapperProvider.mapper();
        jacksonMapper = new JacksonMapper(mapper);
    }
}
