package org.rtb.vexing.handler;

import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class IpHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpRequest;

    @Mock
    private HttpServerResponse httpResponse;

    private CaseInsensitiveHeaders headers;

    private IpHandler handler;

    @Before
    public void setUp() {
        headers = new CaseInsensitiveHeaders();
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(headers);
        given(routingContext.response()).willReturn(httpResponse);

        handler = new IpHandler();
    }

    @Test
    public void shouldFallbackRealIpToRemoteAddressAndForwardedIpToEmptyString() {
        // given
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        headers.add("User-Agent", "UnitTest");

        // when
        handler.handle(routingContext);

        //then
        final String response = captureResponseBody();
        assertThat(response).isEqualTo("User Agent: UnitTest\n" +
                "IP: 192.168.244.1\n" +
                "Port: 0\n" +
                "Forwarded IP: \n" +
                "Real IP: 192.168.244.1\n" +
                "User-Agent: UnitTest");
    }

    @Test
    public void shouldFallbackRealIpToForwardedIp() {
        // given
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        headers.add("User-Agent", "UnitTest");
        headers.add("X-Forwarded-For", "203.0.113.195");

        // when
        handler.handle(routingContext);

        //then
        final String response = captureResponseBody();
        assertThat(response).isEqualTo("User Agent: UnitTest\n" +
                "IP: 192.168.244.1\n" +
                "Port: 0\n" +
                "Forwarded IP: 203.0.113.195\n" +
                "Real IP: 203.0.113.195\n" +
                "User-Agent: UnitTest\n" +
                "X-Forwarded-For: 203.0.113.195");
    }

    @Test
    public void shouldFallbackForwardedIPToXRealIpHeaderValue() {
        // given
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        headers.add("User-Agent", "UnitTest");
        headers.add("X-Real-IP", "203.0.113.195");

        // when
        handler.handle(routingContext);

        //then
        final String response = captureResponseBody();
        assertThat(response).isEqualTo("User Agent: UnitTest\n" +
                "IP: 192.168.244.1\n" +
                "Port: 0\n" +
                "Forwarded IP: 203.0.113.195\n" +
                "Real IP: 203.0.113.195\n" +
                "User-Agent: UnitTest\n" +
                "X-Real-IP: 203.0.113.195");
    }

    @Test
    public void shouldWriteIPInfoIntoResponse() {
        // given
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        headers.add("User-Agent", "UnitTest");
        headers.add("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        headers.add("X-Real-IP", "54.83.132.159");
        headers.add("Content-Type", "application/json");
        headers.add("Test-Header", "test-header-value");

        // when
        handler.handle(routingContext);

        //then
        final String response = captureResponseBody();
        assertThat(response).isEqualTo("User Agent: UnitTest\n" +
                "IP: 192.168.244.1\n" +
                "Port: 0\n" +
                "Forwarded IP: 203.0.113.195\n" +
                "Real IP: 203.0.113.195\n" +
                "User-Agent: UnitTest\n" +
                "X-Forwarded-For: 203.0.113.195, 70.41.3.18, 150.172.238.178\n" +
                "X-Real-IP: 54.83.132.159\n" +
                "Content-Type: application/json\n" +
                "Test-Header: test-header-value");
    }

    @Test
    public void shouldExtractOnlyServerIpValueFromXForwardedForHeader() {
        // given
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        headers.add("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        // when
        handler.handle(routingContext);

        //then
        final String response = captureResponseBody();
        assertThat(response).isEqualTo("User Agent: \n" +
                "IP: 192.168.244.1\n" +
                "Port: 0\n" +
                "Forwarded IP: 203.0.113.195\n" +
                "Real IP: 203.0.113.195\n" +
                "X-Forwarded-For: 203.0.113.195, 70.41.3.18, 150.172.238.178");
    }

    private String captureResponseBody() {
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(bodyCaptor.capture());
        return bodyCaptor.getValue();
    }
}