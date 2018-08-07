package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.metric.Metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    public void shouldIncrementActiveConnectionsMetrics() {
        // when
        connectionHandler.handle(httpConnection);

        // then
        verify(metrics).updateActiveConnectionsMetrics(eq(true));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldDecrementActiveConnectionsMetrics() {
        // when
        connectionHandler.handle(httpConnection);

        // then
        final ArgumentCaptor<Handler<Void>> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(httpConnection).closeHandler(handlerCaptor.capture());
        handlerCaptor.getValue().handle(null); // emulate closing of http connection

        final ArgumentCaptor<Boolean> incrementCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(metrics, times(2)).updateActiveConnectionsMetrics(incrementCaptor.capture());
        assertThat(incrementCaptor.getAllValues()).containsExactly(true, false);
    }
}
