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
import org.prebid.server.proto.response.BidderInfo;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    private BidderDetailsHandler handler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(httpRequest.getParam(anyString())).willReturn("bidderName1");
        given(bidderCatalog.names()).willReturn(singleton("bidderName1"));
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo());

        handler = new BidderDetailsHandler(bidderCatalog);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BidderDetailsHandler(null));
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        handler = new BidderDetailsHandler(bidderCatalog);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithHttpStatus404IfNoBidderFound() {
        // given
        given(bidderCatalog.names()).willReturn(emptySet());
        handler = new BidderDetailsHandler(bidderCatalog);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(404);
    }

    @Test
    public void shouldRespondWithExpectedBody() {
        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"enabled\":true,\"maintainer\":{\"email\":\"test@email.org\"},\"capabilities\":"
                        + "{\"app\":{\"mediaTypes\":[\"mediaType1\"]},\"site\":{\"mediaTypes\":[\"mediaType2\"]}},"
                        + "\"gdpr\":{\"vendorId\":0,\"enforced\":true}}"));
    }

    @Test
    public void shouldRespondWithExpectedBodyForBidderAlias() {
        // given
        given(bidderCatalog.isAlias(anyString())).willReturn(true);
        given(bidderCatalog.nameByAlias(anyString())).willReturn("bidderName1");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"enabled\":true,\"maintainer\":{\"email\":\"test@email.org\"},\"capabilities\":"
                        + "{\"app\":{\"mediaTypes\":[\"mediaType1\"]},\"site\":{\"mediaTypes\":[\"mediaType2\"]}},"
                        + "\"gdpr\":{\"vendorId\":0,\"enforced\":true},\"aliasOf\":\"bidderName1\"}"));
    }

    private static BidderInfo givenBidderInfo() {
        return BidderInfo.create(true, "test@email.org",
                singletonList("mediaType1"), singletonList("mediaType2"), null, 0, true);
    }
}
