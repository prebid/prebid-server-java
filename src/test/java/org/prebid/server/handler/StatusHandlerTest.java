package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.health.HealthChecker;
import org.prebid.server.health.model.StatusResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class StatusHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private HealthChecker healthCheck;

    private StatusHandler statusHandler;

    @Test
    public void creationShouldFailOnNullHealthCheckers() {
        assertThatNullPointerException().isThrownBy(() -> new StatusHandler(null));
    }

    @Test
    public void shouldRespondHttp200OkWithExpectedBody() throws JsonProcessingException {
        // given
        final ZonedDateTime testTime = ZonedDateTime.now(Clock.systemUTC());
        statusHandler = new StatusHandler(Arrays.asList(healthCheck, healthCheck, healthCheck));

        given(routingContext.response()).willReturn(httpResponse);
        given(healthCheck.name()).willReturn("application", "db", "other");
        given(healthCheck.status()).willReturn(StatusResponse.of("ready", null),
                StatusResponse.of("UP", testTime), StatusResponse.of("DOWN", testTime));

        // when
        statusHandler.handle(routingContext);

        // then
        final Map<String, StatusResponse> expectedMap = new TreeMap<>();
        expectedMap.put("application", StatusResponse.of("ready", null));
        expectedMap.put("db", StatusResponse.of("UP", testTime));
        expectedMap.put("other", StatusResponse.of("DOWN", testTime));

        verify(httpResponse).end(eq(mapper.writeValueAsString(expectedMap)));
    }

    @Test
    public void shouldRespondWithNoContentWhenMessageWasNotDefined() {
        statusHandler = new StatusHandler(emptyList());
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(eq(204))).willReturn(httpResponse);

        // when
        statusHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(204));
    }
}
