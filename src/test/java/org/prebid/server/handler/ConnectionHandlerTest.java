package org.prebid.server.handler;

import io.vertx.core.http.HttpConnection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.metric.Metrics;

public class ConnectionHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpConnection httpConnection;

    private ConnectionHandler connectionHandler;

    @Mock
    private Metrics metrics;

    @Before
    public void setUp() {
        connectionHandler = ConnectionHandler.create(metrics);
    }

    @Test
    public void shouldIncrementNetworkErrorsMetrics() {
        // FIXME
    }

    @Test
    public void shouldDecrementNetworkErrorsMetrics() {
        // FIXME
    }
}
