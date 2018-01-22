package org.rtb.vexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.Json;
import org.junit.BeforeClass;
import org.rtb.vexing.json.ObjectMapperConfigurer;

import java.io.IOException;

public abstract class VertxTest {

    protected static ObjectMapper mapper;
    // RubiconParams and UsersyncInfo fields are not in snake-case
    protected static ObjectMapper defaultNamingMapper;

    @BeforeClass
    public static void beforeClass() {
        ObjectMapperConfigurer.configure();
        mapper = Json.mapper;
        defaultNamingMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
