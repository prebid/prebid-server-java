package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DealsStatusHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private DealsStatusHandler dealsStatusHandler;

    @Mock
    private DeliveryProgressService deliveryProgressService;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerResponse httpServerResponse;

    @Before
    public void setUp() {
        dealsStatusHandler = new DealsStatusHandler(deliveryProgressService, jacksonMapper);
        given(routingContext.response()).willReturn(httpServerResponse);
        given(httpServerResponse.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpServerResponse);
        given(httpServerResponse.closed()).willReturn(false);
        given(httpServerResponse.exceptionHandler(any())).willReturn(httpServerResponse);
    }

    @Test
    public void handleShouldNotSendDeliveryProgressReportWhenClientHasGone() {
        // given
        given(httpServerResponse.closed()).willReturn(true);

        // when
        dealsStatusHandler.handle(routingContext);

        // then
        verify(httpServerResponse, never()).end();
    }

    @Test
    public void handleShouldReturnDeliveryProgressReport() throws IOException {
        // given
        given(deliveryProgressService.getOverallDeliveryProgressReport())
                .willReturn(DeliveryProgressReport.builder().reportId("reportId").build());

        // when
        dealsStatusHandler.handle(routingContext);

        // then
        final String responseBody = getResponseBody();
        verify(httpServerResponse).putHeader(eq(HttpUtil.CONTENT_TYPE_HEADER), eq(HttpHeaderValues.APPLICATION_JSON));
        assertThat(mapper.readValue(responseBody, DeliveryProgressReport.class))
                .isEqualTo(DeliveryProgressReport.builder().reportId("reportId").build());
    }

    private String getResponseBody() {
        final ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpServerResponse).end(bodyCaptor.capture());
        return bodyCaptor.getValue();
    }
}
