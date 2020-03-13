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

import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
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
    public void shouldRespondWithExpectedBodyAndExcludeNotActiveBidders() {
        // given
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder2", "bidder3", "bidder1")));
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.isActive(eq("bidder3"))).willReturn(false);

        handler = new BiddersHandler(bidderCatalog, jacksonMapper);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("[\"bidder1\",\"bidder2\"]"));
    }

    @Test
    public void shouldRespondWithExpectedBodyAndExcludeNotActiveBidderAliases() {
        // given
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("bidder1", "bidder2")));
        given(bidderCatalog.aliases()).willReturn(new HashSet<>(asList("bidder1-alias", "bidder2-alias")));

        given(bidderCatalog.nameByAlias(eq("bidder1-alias"))).willReturn("bidder1");
        given(bidderCatalog.nameByAlias(eq("bidder2-alias"))).willReturn("bidder2");

        given(bidderCatalog.isActive(eq("bidder1"))).willReturn(true);
        given(bidderCatalog.isActive(eq("bidder2"))).willReturn(false);

        handler = new BiddersHandler(bidderCatalog, jacksonMapper);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).end(eq("[\"bidder1\",\"bidder1-alias\"]"));
    }
}
