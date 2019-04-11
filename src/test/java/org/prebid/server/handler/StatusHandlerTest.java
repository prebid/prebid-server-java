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
import org.prebid.server.health.model.Status;
import org.prebid.server.health.model.StatusResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
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
    public void shouldRespondHttp200OkWithExpectedBody() throws JsonProcessingException {
        // given
        final ZonedDateTime testTime = ZonedDateTime.now(Clock.systemUTC());
        statusHandler = new StatusHandler("ready", Arrays.asList(healthCheck, healthCheck));

        given(routingContext.response()).willReturn(httpResponse);
        given(healthCheck.getCheckName()).willReturn("db", "other");
        given(healthCheck.getLastStatus())
                .willReturn(StatusResponse.of(Status.UP, testTime), StatusResponse.of(Status.DOWN, testTime));

        // when
        statusHandler.handle(routingContext);

        // then
        final Map<String, Object> expectedMap = new TreeMap<>();
        expectedMap.put("application", "ready");
        expectedMap.put("db", StatusResponse.of(Status.UP, testTime));
        expectedMap.put("other", StatusResponse.of(Status.DOWN, testTime));

        verify(httpResponse).end(eq(mapper.writeValueAsString(expectedMap)));
    }

    @Test
    public void shouldRespondOnlyWithApplicationReadyIfNoHealthCheckersProvided() throws JsonProcessingException {
        // given
        statusHandler = new StatusHandler("ready", emptyList());
        given(routingContext.response()).willReturn(httpResponse);

        // when
        statusHandler.handle(routingContext);

        // then
        verify(httpResponse).end(eq(mapper.writeValueAsString(singletonMap("application", "ready"))));
    }

    @Test
    public void shouldRespondWithNoContentWhenMessageWasNotDefined() {
        statusHandler = new StatusHandler(null, singletonList(healthCheck));
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(eq(204))).willReturn(httpResponse);

        // when
        statusHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(204));
    }
}
