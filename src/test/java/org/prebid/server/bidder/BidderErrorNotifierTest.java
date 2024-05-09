package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class BidderErrorNotifierTest extends VertxTest {

    private static final byte[] EMPTY_BODY = "{}".getBytes();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private Metrics metrics;
    @Mock
    private Bidder<BidRequest> bidder;

    private BidderErrorNotifier target;

    @Before
    public void setUp() {
        target = new BidderErrorNotifier(200, true, false, 1d, httpClient, metrics);
    }

    @Test
    public void shouldNotSendTimeoutNotificationWhenBidderDoesNotCreateRequest() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();

        given(bidder.makeTimeoutNotification(any())).willReturn(null);

        // when
        target.processTimeout(
                BidderCall.failedHttp(bidderRequest, BidderError.timeout("Timeout")), bidder);

        // then
        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verifyNoInteractions(httpClient);
    }

    @Test
    public void shouldSendTimeoutNotificationAndUpdateSuccessMetric() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();
        final BidderCall<BidRequest> bidderCall = BidderCall.failedHttp(
                bidderRequest, BidderError.timeout("Timeout"));

        given(bidder.makeTimeoutNotification(any())).willReturn(HttpRequest.<Void>builder()
                .uri("url")
                .method(HttpMethod.POST)
                .body(EMPTY_BODY)
                .build());

        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)));

        // when
        final BidderCall<BidRequest> result = target.processTimeout(bidderCall, bidder);

        // then
        Assertions.assertThat(result).isSameAs(bidderCall);

        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verify(httpClient).request(eq(HttpMethod.POST), eq("url"), isNull(), eq(EMPTY_BODY), eq(200L));
        verify(metrics).updateTimeoutNotificationMetric(eq(true));
    }

    @Test
    public void shouldSendTimeoutNotificationAndUpdateFailedMetricWhenResponseCodeNonSuccess() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();

        given(bidder.makeTimeoutNotification(any())).willReturn(HttpRequest.<Void>builder()
                .uri("url")
                .method(HttpMethod.POST)
                .body(EMPTY_BODY)
                .build());

        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(404, null, null)));

        // when
        target.processTimeout(
                BidderCall.failedHttp(bidderRequest, BidderError.timeout("Timeout")), bidder);

        // then
        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verify(httpClient).request(eq(HttpMethod.POST), eq("url"), isNull(), eq(EMPTY_BODY), eq(200L));
        verify(metrics).updateTimeoutNotificationMetric(eq(false));
    }

    @Test
    public void shouldSendTimeoutNotificationAndUpdateFailedMetricWhenResponseTimedOut() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();

        given(bidder.makeTimeoutNotification(any())).willReturn(HttpRequest.<Void>builder()
                .uri("url")
                .method(HttpMethod.POST)
                .body(EMPTY_BODY)
                .build());

        given(httpClient.request(any(), anyString(), any(), any(byte[].class), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("Timeout exception")));

        // when
        target.processTimeout(
                BidderCall.failedHttp(bidderRequest, BidderError.timeout("Timeout")), bidder);

        // then
        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verify(httpClient).request(eq(HttpMethod.POST), eq("url"), isNull(), eq(EMPTY_BODY), eq(200L));
        verify(metrics).updateTimeoutNotificationMetric(eq(false));
    }
}
