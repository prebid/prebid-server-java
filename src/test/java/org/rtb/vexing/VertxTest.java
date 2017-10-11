package org.rtb.vexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.Json;
import org.junit.BeforeClass;
import org.rtb.vexing.json.ObjectMapperConfigurer;

public abstract class VertxTest {

    protected static ObjectMapper mapper;
    // RubiconParams fields are in camel-case as opposed to all other fields which are in snake-case
    protected static ObjectMapper rubiconParamsMapper;

    @BeforeClass
    public static void beforeClass() {
        ObjectMapperConfigurer.configure();
        mapper = Json.mapper;
        rubiconParamsMapper = new ObjectMapper();
    }
}
