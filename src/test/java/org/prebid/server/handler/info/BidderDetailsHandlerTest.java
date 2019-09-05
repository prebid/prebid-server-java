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

        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidderName1", "bidderName2")));
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo());
        given(bidderCatalog.isActive("bidderName1")).willReturn(true);
        given(bidderCatalog.isActive("bidderName2")).willReturn(false);

        given(bidderCatalog.aliases()).willReturn(new HashSet<>(asList("bidderAlias1", "bidderAlias2")));
        given(bidderCatalog.nameByAlias("bidderAlias1")).willReturn("bidderName1");
        given(bidderCatalog.nameByAlias("bidderAlias2")).willReturn("bidderName2");

        handler = new BidderDetailsHandler(bidderCatalog);
    }

    @Test
    public void creationShouldFailIfAllAliasIsConfigured() {
        given(bidderCatalog.aliases()).willReturn(singleton("all"));
        assertThatIllegalArgumentException().isThrownBy(() -> new BidderDetailsHandler(bidderCatalog));
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        handler = new BidderDetailsHandler(bidderCatalog);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
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
    public void shouldRespondWithHttpStatus404IfBidderIsDisabled() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("bidderName2");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(404);
    }

    @Test
    public void shouldRespondWithHttpStatus404IfBidderAliasIsDisabled() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("bidderAlias2");

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
                eq("{\"maintainer\":{\"email\":\"test@email.org\"},\"capabilities\":{\"app\":"
                        + "{\"mediaTypes\":[\"mediaType1\"]},\"site\":{\"mediaTypes\":[\"mediaType2\"]}}}"));
    }

    @Test
    public void shouldRespondWithExpectedBodyForBidderAlias() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("bidderAlias1");

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(
                eq("{\"maintainer\":{\"email\":\"test@email.org\"},\"capabilities\":{\"app\":"
                        + "{\"mediaTypes\":[\"mediaType1\"]},\"site\":{\"mediaTypes\":[\"mediaType2\"]}},"
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
                eq("{\"bidderAlias1\":{\"maintainer\":{\"email\":\"test@email.org\"},\"capabilities\":"
                        + "{\"app\":{\"mediaTypes\":[\"mediaType1\"]},\"site\":{\"mediaTypes\":[\"mediaType2\"]}},"
                        + "\"aliasOf\":\"bidderName1\"},"
                        + "\"bidderName1\":{\"maintainer\":{\"email\":\"test@email.org\"},\"capabilities\":"
                        + "{\"app\":{\"mediaTypes\":[\"mediaType1\"]},\"site\":{\"mediaTypes\":[\"mediaType2\"]}}}}"));
    }

    private static BidderInfo givenBidderInfo() {
        return BidderInfo.create(true, "test@email.org", singletonList("mediaType1"),
                singletonList("mediaType2"), null, 0, true, false);
    }
}
