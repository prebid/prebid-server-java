package org.prebid.server.handler.info;

import io.netty.util.AsciiString;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class BidderDetailsHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private BidderDetailsHandler handler;
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

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(httpRequest.getParam(anyString())).willReturn("bidderName1");

        given(bidderCatalog.names()).willReturn(new HashSet<>(
                asList("bidderName1", "bidderName2", "bidderAlias1", "bidderAlias2")));
        given(bidderCatalog.bidderInfoByName("bidderName1")).willReturn(givenBidderInfo());
        given(bidderCatalog.bidderInfoByName("bidderAlias2")).willReturn(givenBidderInfo());
        given(bidderCatalog.bidderInfoByName(eq("bidderName2")))
                .willReturn(givenBidderInfo(false, "http://", null));
        given(bidderCatalog.bidderInfoByName(eq("bidderAlias1")))
                .willReturn(givenBidderInfo(false, "http://", "bidderName1"));

        handler = new BidderDetailsHandler(bidderCatalog, jacksonMapper);
    }

    @Test
    public void creationShouldFailIfAllNameIsConfigured() {
        given(bidderCatalog.names()).willReturn(singleton("all"));
        assertThatIllegalArgumentException().isThrownBy(() -> new BidderDetailsHandler(bidderCatalog, jacksonMapper));
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithHttpStatus404IfNoBidderFound() {
        // given
        given(bidderCatalog.names()).willReturn(emptySet());
        handler = new BidderDetailsHandler(bidderCatalog, jacksonMapper);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(404);
    }

    @Test
    public void shouldRespondWithExpectedBodyForDisabledBidder() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("bidderName2");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end("{\"status\":\"DISABLED\",\"usesHttps\":false,"
                + "\"maintainer\":{\"email\":\"test@email.org\"},"
                + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                + "\"site\":{\"mediaTypes\":[\"audio\"]},\"dooh\":{\"mediaTypes\":[\"native\"]}}}");
    }

    @Test
    public void shouldRespondWithExpecteddBodyForDisabledAlias() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("bidderAlias2");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end("{\"status\":\"ACTIVE\",\"usesHttps\":true,"
                + "\"maintainer\":{\"email\":\"test@email.org\"},"
                + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                + "\"site\":{\"mediaTypes\":[\"audio\"]},\"dooh\":{\"mediaTypes\":[\"native\"]}}}");
    }

    @Test
    public void shouldRespondWithExpectedBody() {
        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"status\":\"ACTIVE\",\"usesHttps\":true,\"maintainer\":{\"email\":\"test@email.org\"},"
                        + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                        + "\"site\":{\"mediaTypes\":[\"audio\"]},\"dooh\":{\"mediaTypes\":[\"native\"]}}}"));
    }

    @Test
    public void shouldRespondWithExpectedBodyForBidderAlias() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("bidderAlias1");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"status\":\"DISABLED\",\"usesHttps\":false,\"maintainer\":{\"email\":\"test@email.org\"},"
                        + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                        + "\"site\":{\"mediaTypes\":[\"audio\"]},\"dooh\":{\"mediaTypes\":[\"native\"]}},"
                        + "\"aliasOf\":\"bidderName1\"}"));
    }

    @Test
    public void shouldRespondWithExpectedBodyForAllQueryParam() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("all");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"bidderAlias1\":{\"status\":\"DISABLED\",\"usesHttps\":false,"
                        + "\"maintainer\":{\"email\":\"test@email.org\"},"
                        + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                        + "\"site\":{\"mediaTypes\":[\"audio\"]},"
                        + "\"dooh\":{\"mediaTypes\":[\"native\"]}},\"aliasOf\":\"bidderName1\"},"
                        + "\"bidderAlias2\":{\"status\":\"ACTIVE\",\"usesHttps\":true,"
                        + "\"maintainer\":{\"email\":\"test@email.org\"},"
                        + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                        + "\"site\":{\"mediaTypes\":[\"audio\"]},"
                        + "\"dooh\":{\"mediaTypes\":[\"native\"]}}},\"bidderName1\":{\"status\":\"ACTIVE\","
                        + "\"usesHttps\":true,\"maintainer\":{\"email\":\"test@email.org\"},"
                        + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                        + "\"site\":{\"mediaTypes\":[\"audio\"]},\"dooh\":{\"mediaTypes\":[\"native\"]}}},"
                        + "\"bidderName2\":{\"status\":\"DISABLED\",\"usesHttps\":false,"
                        + "\"maintainer\":{\"email\":\"test@email.org\"},"
                        + "\"capabilities\":{\"app\":{\"mediaTypes\":[\"banner\"]},"
                        + "\"site\":{\"mediaTypes\":[\"audio\"]},\"dooh\":{\"mediaTypes\":[\"native\"]}}}}"));
    }

    private static BidderInfo givenBidderInfo(boolean enabled, String endpoint, String aliasOf) {
        return BidderInfo.create(
                enabled,
                null,
                true,
                endpoint,
                aliasOf,
                "test@email.org",
                singletonList(MediaType.BANNER),
                singletonList(MediaType.AUDIO),
                singletonList(MediaType.NATIVE),
                null,
                0,
                true,
                false,
                CompressionType.NONE);
    }

    private static BidderInfo givenBidderInfo() {
        return givenBidderInfo(true, "https://endpoint.com", null);
    }
}
