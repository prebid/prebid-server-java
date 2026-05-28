package org.prebid.server.vertx.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.impl.RoutingContextInternal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ParametrizedDecompressionHandlerTest {

    @Mock(strictness = LENIENT)
    private RoutingContextInternal routingContext;
    @Mock(strictness = LENIENT)
    private HttpServerRequest request;
    @Mock(strictness = LENIENT)
    private HttpServerResponse response;
    @Mock(strictness = LENIENT)
    private RequestBody requestBody;

    private final ParametrizedDecompressionHandler target = new ParametrizedDecompressionHandler(1024);

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(request);
        given(routingContext.response()).willReturn(response);
        given(routingContext.body()).willReturn(requestBody);

        given(response.setStatusCode(anyInt())).willReturn(response);
    }

    @Test
    public void handleShouldPassRequestToNextHandlerWhenGzipNotSet() {
        // given and when
        target.handle(routingContext);

        // then
        verify(routingContext).next();
        verify(routingContext, times(0)).setBody(any());
    }

    @Test
    public void handleShouldDecompressRequestAndSetBodyWhenGzipParamIsSetToOne() {
        // given
        final byte[] body = "decompressed body".getBytes(StandardCharsets.UTF_8);

        given(request.getParam(eq("gzip"))).willReturn("1");
        given(requestBody.buffer()).willReturn(Buffer.buffer(gzip(body)));

        // when
        target.handle(routingContext);

        // then
        verify(routingContext, times(1)).setBody(eq(Buffer.buffer(body)));
        verify(routingContext).next();
    }

    @Test
    public void handleShouldDecompressRequestAndSetBodyWhenGzipParamIsSetToTrue() {
        // given
        final byte[] body = "decompressed body".getBytes(StandardCharsets.UTF_8);

        given(request.getParam(eq("gzip"))).willReturn("true");
        given(requestBody.buffer()).willReturn(Buffer.buffer(gzip(body)));

        // when
        target.handle(routingContext);

        // then
        verify(routingContext, times(1)).setBody(eq(Buffer.buffer(body)));
        verify(routingContext).next();
    }

    @Test
    public void handleShouldRespondWithBadRequestWhenCompressedBodyIsCorrupted() {
        // given
        final byte[] body = new byte[]{1, 2, 3, 4};

        given(request.getParam(eq("gzip"))).willReturn("true");
        given(requestBody.buffer()).willReturn(Buffer.buffer(body));

        // when
        target.handle(routingContext);

        // then
        verify(response).setStatusCode(HttpResponseStatus.BAD_REQUEST.code());

        final ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(responseBodyCaptor.capture());
        assertThat(responseBodyCaptor.getValue()).asString().startsWith("Invalid body: ");

        verify(routingContext, times(0)).setBody(any());
        verify(routingContext, times(0)).next();
    }

    private static byte[] gzip(byte[] input) {
        try (
                ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj)) {

            gzip.write(input);
            gzip.finish();

            return obj.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
