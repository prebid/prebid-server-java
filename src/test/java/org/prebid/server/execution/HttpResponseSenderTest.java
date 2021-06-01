package org.prebid.server.execution;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HttpResponseSenderTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private Logger logger;

    private HttpResponseSender responseSender;

    @Before
    public void setUp() {
        given(routingContext.response()).willReturn(httpResponse);

        responseSender = HttpResponseSender.from(routingContext, logger);
    }

    @Test
    public void fromShouldReturnNewInstance() {
        // when
        responseSender = HttpResponseSender.from(null, null);

        // then
        assertThat(responseSender).isNotNull();
    }

    @Test
    public void shouldUseExceptionHandler() {
        // given
        @SuppressWarnings("unchecked") final Handler<Throwable> exceptionHandler = mock(Handler.class);
        responseSender.exceptionHandler(exceptionHandler);

        // when
        responseSender.send();

        // then
        verify(httpResponse).exceptionHandler(eq(exceptionHandler));
    }

    @Test
    public void shouldUseStatus() {
        // given
        responseSender.status(HttpResponseStatus.OK);

        // when
        responseSender.send();

        // then
        verify(httpResponse).setStatusCode(200);
        verify(httpResponse).setStatusMessage("OK");
    }

    @Test
    public void shouldUseHeaders() {
        // given
        responseSender.headers(singletonMap(AsciiString.of("name"), AsciiString.of("value")));

        // when
        responseSender.send();

        // then
        verify(httpResponse).putHeader(AsciiString.of("name"), AsciiString.of("value"));
    }

    @Test
    public void shouldUseBody() {
        // given
        responseSender.body("body");

        // when
        responseSender.send();

        // then
        verify(httpResponse).end(eq("body"));
    }

    @Test
    public void shouldUseBodyAsBuffer() {
        // given
        responseSender.body(Buffer.buffer("body"));

        // when
        responseSender.send();

        // then
        verify(httpResponse).end(eq(Buffer.buffer("body")));
    }

    @Test
    public void shouldUseStringBodyInsteadOfBodyAsBuffer() {
        // given
        responseSender.body("body");
        responseSender.body(Buffer.buffer("body"));

        // when
        responseSender.send();

        // then
        verify(httpResponse).end(eq("body"));
    }

    @Test
    public void sendShouldReturnFalseIfConnectionIsAlreadyClosed() {
        // given
        given(httpResponse.closed()).willReturn(true);

        // when
        final boolean result = responseSender.send();

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void sendFileShouldUseGivenFile() {
        // when
        responseSender.sendFile("filename");

        // then
        verify(httpResponse).sendFile("filename");
    }
}
