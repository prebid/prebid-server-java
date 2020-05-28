package org.prebid.server.handler.openrtb2.aspects;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.handler.openrtb2.VideoHandler;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.spring.config.WebConfiguration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class QueuedRequestTimeoutTest extends VertxTest {

    public static final String TEST_TIME_IN_QUEUE_HEADER = "test-time-in-queue";
    public static final String TEST_TIMEOUT_IN_QUEUE_HEADER = "test-timeout-in-queue";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private QueuedRequestTimeout queuedRequestTimeout;
    @Mock
    private VideoHandler videoHandler;
    @Mock
    private Metrics metrics;
    @Mock
    private WebConfiguration.RequestTimeoutHeaders requestTimeoutHeaders;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(httpResponse.exceptionHandler(any())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(new CaseInsensitiveHeaders());

        given(requestTimeoutHeaders.getRequestTimeInQueue()).willReturn(TEST_TIME_IN_QUEUE_HEADER);
        given(requestTimeoutHeaders.getRequestTimeoutInQueue()).willReturn(TEST_TIMEOUT_IN_QUEUE_HEADER);

        queuedRequestTimeout = new QueuedRequestTimeout(videoHandler, requestTimeoutHeaders, metrics, MetricName.video);
    }

    @Test
    public void queuedRequestTimeoutShouldRunVideoHandler() {
        // given
        final VertxHttpHeaders headers = new VertxHttpHeaders();
        headers.add(TEST_TIME_IN_QUEUE_HEADER, "0.9");
        headers.add(TEST_TIMEOUT_IN_QUEUE_HEADER, "5");
        given(httpRequest.headers()).willReturn(headers);

        // when
        queuedRequestTimeout.handle(routingContext);

        // then
        verify(videoHandler).handle(routingContext);
        verify(metrics).updateQueuedRequestMetrics(MetricName.video, true, 900);
    }

    @Test
    public void queuedRequestTimeoutShouldRunVideoHandlerIfHeadersAreEmpty() {
        // given
        final VertxHttpHeaders headers = new VertxHttpHeaders();
        headers.add(TEST_TIME_IN_QUEUE_HEADER, "");
        headers.add(TEST_TIMEOUT_IN_QUEUE_HEADER, "");
        given(httpRequest.headers()).willReturn(headers);

        // when
        queuedRequestTimeout.handle(routingContext);

        // then
        verify(videoHandler).handle(routingContext);
    }

    @Test
    public void queuedRequestTimeoutShouldReturnErrorIfIncorrectHeaderParams() {
        // given
        final VertxHttpHeaders headers = new VertxHttpHeaders();
        headers.add(TEST_TIME_IN_QUEUE_HEADER, "test1");
        headers.add(TEST_TIMEOUT_IN_QUEUE_HEADER, "test2");
        given(httpRequest.headers()).willReturn(headers);

        // when
        queuedRequestTimeout.handle(routingContext);

        // then
        verify(httpResponse).end("Request timeout headers are incorrect (wrong format)");
    }

    @Test
    public void queuedRequestTimeoutShouldReturnTimeoutError() {
        // given
        final VertxHttpHeaders headers = new VertxHttpHeaders();
        headers.add(TEST_TIME_IN_QUEUE_HEADER, "6");
        headers.add(TEST_TIMEOUT_IN_QUEUE_HEADER, "5");
        given(httpRequest.headers()).willReturn(headers);

        // when
        queuedRequestTimeout.handle(routingContext);

        // then
        verify(httpResponse).end("Queued request processing time exceeded maximum");
        verify(metrics).updateQueuedRequestMetrics(MetricName.video, false, 6000);
    }

}
