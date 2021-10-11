package org.prebid.server.handler;

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
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class ParamHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Mock
    private BidderCatalog bidderCatalog;

    @Mock
    private HttpServerResponse httpResponse;

    private BidderParamHandler handler;

    @Before
    public void setUp() {
        handler = new BidderParamHandler(
                BidderParamValidator.create(bidderCatalog, "static/bidder-params", jacksonMapper));
        given(routingContext.response()).willReturn(httpResponse);
    }

    @Test
    public void shouldReturnCorrectHeaders() {
        //given
        handler.handle(routingContext);

        //then
        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
    }
}
