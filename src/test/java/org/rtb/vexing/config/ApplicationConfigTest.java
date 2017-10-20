package org.rtb.vexing.config;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

public class ApplicationConfigTest extends VertxTest {

    private static final String DEFAULT_FILE = "/org/rtb/vexing/config/test-default-conf.json";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private Context context;
    private JsonObject verticleConfig;

    @Before
    public void setUp() {
        verticleConfig = new JsonObject();
        given(vertx.getOrCreateContext()).willReturn(context);
        given(context.config()).willReturn(verticleConfig);
    }

    @Test
    public void shouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> ApplicationConfig.resolve(null, null));
        assertThatNullPointerException().isThrownBy(() -> ApplicationConfig.resolve(vertx, null));
    }

    @Test
    public void shouldTolerateNonExistingDefaultFile() {
        // when
        assertThatCode(() -> ApplicationConfig.resolve(vertx, "non-existing")).doesNotThrowAnyException();
    }

    @Test
    public void shouldUseDefaultValues() {
        // when
        final Future<JsonObject> configFuture = ApplicationConfig.resolve(vertx, DEFAULT_FILE);

        // then
        final JsonObject config = configFuture.result();
        assertThat(config.getString("param1")).isEqualTo("value1");
        assertThat(config.getString("param2")).isEqualTo("value2");
        assertThat(config.getString("param3.param3_1")).isEqualTo("value3_1");
        assertThat(config.getString("param3.param3_composite1.param3_composite1_1"))
                .isEqualTo("value3_composite1_1");
    }

    @Test
    public void verticleConfigShouldOverrideDefaultValues() {
        // given
        verticleConfig.put("param2", "value2 overridden in verticle");
        verticleConfig.put("param3.param3_1", "value3_1 overridden in verticle");
        verticleConfig.put("param3.param3_composite1.param3_composite1_1",
                "value3_composite1_1 overridden in verticle");

        // when
        final Future<JsonObject> configFuture = ApplicationConfig.resolve(vertx, DEFAULT_FILE);

        // then
        final JsonObject config = configFuture.result();
        assertThat(config.getString("param1")).isEqualTo("value1");
        assertThat(config.getString("param1")).isEqualTo("value1");
        assertThat(config.getString("param3.param3_1")).isEqualTo("value3_1 overridden in verticle");
        assertThat(config.getString("param3.param3_composite1.param3_composite1_1"))
                .isEqualTo("value3_composite1_1 overridden in verticle");
    }
}
