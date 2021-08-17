package org.prebid.server.handler.info;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.vertx.core.MultiMap;
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

import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class BiddersHandlerTest extends VertxTest {

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

    private BiddersHandler handler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "false"));
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(any(Integer.class))).willReturn(httpResponse);
        given(bidderCatalog.names()).willReturn(emptySet());

        handler = new BiddersHandler(bidderCatalog, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BiddersHandler(null, jacksonMapper));
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithExpectedMessageAndStatusBadRequestWhenEnabledOnlyNotProvided() {
        // given
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
        verify(httpResponse).end(eq("Invalid value for 'enabledonly' query param, must be of boolean type"));
    }

    @Test
    public void shouldRespondWithExpectedMessageAndStatusBadRequestWhenEnabledOnlyFlagHasInvalidValue() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "yes"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
        verify(httpResponse).end(eq("Invalid value for 'enabledonly' query param, must be of boolean type"));
    }

    @Test
    public void shouldTolerateWithEnabledOnlyFlagInCaseInsensitiveMode() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "tRuE"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForEnabledOnlyFalseFlag() {
        // given
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));
        handler = new BiddersHandler(bidderCatalog, jacksonMapper);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder1\",\"bidder2\",\"bidder3\"]"));
    }

    @Test
    public void shouldRespondWithExpectedBodyAndStatusOkForEnabledOnlyTrueFlag() {
        // given
        given(routingContext.queryParams())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("enabledonly", "true"));
        given(bidderCatalog.isActive("bidder3")).willReturn(true);
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));
        handler = new BiddersHandler(bidderCatalog, jacksonMapper);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(HttpResponseStatus.OK.code());
        verify(httpResponse).end(eq("[\"bidder3\"]"));
    }
}
