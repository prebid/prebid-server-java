package org.rtb.vexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.Json;
import org.junit.BeforeClass;
import org.rtb.vexing.json.ObjectMapperConfigurer;

public abstract class VertxTest {

    protected static ObjectMapper mapper;

    @BeforeClass
    public static void beforeClass() {
        ObjectMapperConfigurer.configure();
        mapper = Json.mapper;
    }
}
