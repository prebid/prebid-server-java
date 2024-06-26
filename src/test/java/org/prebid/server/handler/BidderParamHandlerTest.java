package org.prebid.server.handler;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.BidderParamValidator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BidderParamHandlerTest {

    @Mock
    private RoutingContext routingContext;
    @Mock
    private BidderParamValidator bidderParamValidator;
    @Mock
    private HttpServerResponse httpResponse;

    private BidderParamHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new BidderParamHandler(bidderParamValidator);

        given(routingContext.response()).willReturn(httpResponse);

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
    }

    @Test
    public void shouldRespondWithCorrectHeaders() {
        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
    }
}
