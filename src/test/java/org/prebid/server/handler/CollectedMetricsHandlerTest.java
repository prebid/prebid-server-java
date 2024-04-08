package org.prebid.server.handler;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.handler.admin.CollectedMetricsHandler;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.TreeMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class CollectedMetricsHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MetricRegistry metricRegistry;
    @Mock
    private Gauge<Integer> gauge;
    @Mock
    private Counter counter;
    @Mock
    private Histogram histogram;
    @Mock
    private Snapshot snapshot;
    @Mock
    private Meter meter;
    @Mock
    private Timer timer;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private CollectedMetricsHandler metricsHandler;

    @Before
    public void setUp() {
        metricsHandler = new CollectedMetricsHandler(metricRegistry, jacksonMapper, "/endpoint");

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpResponse);

        given(metricRegistry.getGauges())
                .willReturn(new TreeMap<>(Collections.singletonMap("gauge", gauge)));
        given(metricRegistry.getCounters())
                .willReturn(new TreeMap<>(Collections.singletonMap("counter", counter)));
        given(metricRegistry.getHistograms())
                .willReturn(new TreeMap<>(Collections.singletonMap("histogram", histogram)));
        given(metricRegistry.getMeters())
                .willReturn(new TreeMap<>(Collections.singletonMap("meter", meter)));
        given(metricRegistry.getTimers())
                .willReturn(new TreeMap<>(Collections.singletonMap("timer", timer)));

        given(gauge.getValue()).willReturn(0);
        given(counter.getCount()).willReturn(0L);
        given(histogram.getSnapshot()).willReturn(snapshot);
        given(snapshot.get95thPercentile()).willReturn(0.0);
        given(meter.getCount()).willReturn(0L);
        given(timer.getCount()).willReturn(0L);
    }

    @Test
    public void handleShouldRespondWithJsonWithAllMetrics() {
        // when
        metricsHandler.handle(routingContext);

        // then
        final ObjectNode expectedResponse = mapper.createObjectNode()
                .put("counter", 0)
                .put("gauge", 0)
                .put("histogram", 0.0)
                .put("meter", 0)
                .put("timer", 0);

        verify(metricRegistry).getGauges();
        verify(metricRegistry).getCounters();
        verify(metricRegistry).getHistograms();
        verify(metricRegistry).getMeters();
        verify(metricRegistry).getTimers();

        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
        verify(httpResponse).end(expectedResponse.toString());
    }
}
