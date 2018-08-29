package org.prebid.server.handler;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
    private Metrics metrics;
    @Mock
    private Vertx vertx;

    private ConnectionHandler connectionHandler;

    @Mock
    private HttpConnection httpConnection;

    @Before
    public void setUp() {
        connectionHandler = ConnectionHandler.create(metrics, vertx, 1000, 3600);
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

    @Test
    public void shouldCloseConnectionIfInboundConnectionsLimitExceeded() {
        // given
        connectionHandler = ConnectionHandler.create(metrics, vertx, 1, 3600);

        // when
        connectionHandler.handle(httpConnection);
        connectionHandler.handle(httpConnection);

        // then
        verify(httpConnection).close();
    }

    @Test
    public void shouldNotCloseConnectionIfInboundConnectionsLimitDoesNotExceeded() {
        // given
        connectionHandler = ConnectionHandler.create(metrics, vertx, 1, 3600);

        // when
        connectionHandler.handle(httpConnection);

        // then
        verify(httpConnection, times(0)).close();
    }

    @Test
    public void shouldDoesNotManageInboundConnectionsIfLimitIsZero() {
        // given
        connectionHandler = ConnectionHandler.create(metrics, vertx, 0, 3600);

        // when
        connectionHandler.handle(httpConnection);

        // then
        verify(httpConnection, times(0)).close();
    }
}
