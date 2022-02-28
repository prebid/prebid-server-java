package org.prebid.server.handler;

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
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.proto.report.LineItemStatusReport;
import org.prebid.server.exception.PreBidException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class LineItemStatusHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private DeliveryProgressService deliveryProgressService;

    private LineItemStatusHandler handler;
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

        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(routingContext.request().getParam(any())).willReturn("lineItemId");

        handler = new LineItemStatusHandler(deliveryProgressService, jacksonMapper, "endpoint");
    }

    @Test
    public void handleShouldRespondWithErrorIfNoLineItemIdSpecified() {
        // given
        given(routingContext.request().getParam(any())).willReturn(null);

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("id parameter is required");
    }

    @Test
    public void handleShouldRespondWithErrorIfProcessingFailed() {
        // given
        given(deliveryProgressService.getLineItemStatusReport(any())).willThrow(new PreBidException("error"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("error");
    }

    @Test
    public void handleShouldRespondWithErrorIfUnexpectedExceptionOccurred() {
        // given
        given(deliveryProgressService.getLineItemStatusReport(any())).willThrow(new RuntimeException("error"));

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(500);
        verify(httpResponse).end("error");
    }

    @Test
    public void handleShouldRespondWithExpectedResult() {
        // given
        given(deliveryProgressService.getLineItemStatusReport(any()))
                .willReturn(LineItemStatusReport.builder().lineItemId("lineItemId").build());

        // when
        handler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(200);
        verify(httpResponse).end("{\"lineItemId\":\"lineItemId\"}");
    }
}
