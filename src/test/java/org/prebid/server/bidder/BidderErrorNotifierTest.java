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
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class BidderErrorNotifierTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private Metrics metrics;

    private BidderErrorNotifier bidderErrorNotifier;

    @Mock
    private Bidder<BidRequest> bidder;

    @Before
    public void setUp() {
        bidderErrorNotifier = new BidderErrorNotifier(200, true, false, 1d, httpClient, metrics);
    }

    @Test
    public void shouldNotSendTimeoutNotificationWhenBidderDoesNotCreateRequest() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();

        given(bidder.makeTimeoutNotification(any())).willReturn(null);

        // when
        bidderErrorNotifier.processTimeout(HttpCall.failure(bidderRequest, BidderError.timeout("Timeout")), bidder);

        // then
        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void shouldSendTimeoutNotificationAndUpdateSuccessMetric() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();
        final HttpCall<BidRequest> bidderCall = HttpCall.failure(bidderRequest, BidderError.timeout("Timeout"));

        given(bidder.makeTimeoutNotification(any())).willReturn(HttpRequest.<Void>builder()
                .uri("url")
                .method(HttpMethod.POST)
                .body("{}")
                .build());

        given(httpClient.request(any(), anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)));

        // when
        final HttpCall<BidRequest> result = bidderErrorNotifier.processTimeout(bidderCall, bidder);

        // then
        Assertions.assertThat(result).isSameAs(bidderCall);

        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verify(httpClient).request(eq(HttpMethod.POST), eq("url"), isNull(), eq("{}"), eq(200L));
        verify(metrics).updateTimeoutNotificationMetric(eq(true));
    }

    @Test
    public void shouldSendTimeoutNotificationAndUpdateFailedMetricWhenResponseCodeNonSuccess() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();

        given(bidder.makeTimeoutNotification(any())).willReturn(HttpRequest.<Void>builder()
                .uri("url")
                .method(HttpMethod.POST)
                .body("{}")
                .build());

        given(httpClient.request(any(), anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(404, null, null)));

        // when
        bidderErrorNotifier.processTimeout(HttpCall.failure(bidderRequest, BidderError.timeout("Timeout")), bidder);

        // then
        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verify(httpClient).request(eq(HttpMethod.POST), eq("url"), isNull(), eq("{}"), eq(200L));
        verify(metrics).updateTimeoutNotificationMetric(eq(false));
    }

    @Test
    public void shouldSendTimeoutNotificationAndUpdateFailedMetricWhenResponseTimedOut() {
        // given
        final HttpRequest<BidRequest> bidderRequest = HttpRequest.<BidRequest>builder().build();

        given(bidder.makeTimeoutNotification(any())).willReturn(HttpRequest.<Void>builder()
                .uri("url")
                .method(HttpMethod.POST)
                .body("{}")
                .build());

        given(httpClient.request(any(), anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("Timeout exception")));

        // when
        bidderErrorNotifier.processTimeout(HttpCall.failure(bidderRequest, BidderError.timeout("Timeout")), bidder);

        // then
        verify(bidder).makeTimeoutNotification(eq(bidderRequest));
        verify(httpClient).request(eq(HttpMethod.POST), eq("url"), isNull(), eq("{}"), eq(200L));
        verify(metrics).updateTimeoutNotificationMetric(eq(false));
    }
}
