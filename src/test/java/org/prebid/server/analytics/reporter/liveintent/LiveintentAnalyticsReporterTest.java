package org.prebid.server.analytics.reporter.liveintent;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.reporter.liveintent.model.LiveIntentAnalyticsProperties;
import org.prebid.server.analytics.reporter.liveintent.model.PbsjBid;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.ListUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;
import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LiveintentAnalyticsReporterTest extends VertxTest {

    @Mock
    private HttpClient httpClient;

    @Captor
    private ArgumentCaptor<String> jsonCaptor;

    private LiveIntentAnalyticsReporter target;

    private LiveIntentAnalyticsProperties properties;

    private TypeReference<List<PbsjBid>> pbjsCollectionType;

    @BeforeEach
    public void setUp() {
        pbjsCollectionType = new TypeReference<>() { };

        properties = LiveIntentAnalyticsProperties.builder()
                .analyticsEndpoint("https://localhost:8080")
                .partnerId("pbsj")
                .timeoutMs(1000L)
                .build();

        target = new LiveIntentAnalyticsReporter(
                properties,
                httpClient,
                jacksonMapper);
    }

    @Test
    public void shouldProcessNotificationEvent() {
        // given
        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(httpClient.get(anyString(), anyLong())).thenReturn(Future.succeededFuture(mockResponse));

        // when
        target.processEvent(NotificationEvent.builder().bidId("123").bidder("foo").build());

        // then
        // Verify that the HTTP client was called with the expected parameters
        verify(httpClient).get(eq(properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-winning-bid"
                + "?b=foo&bidId=123"), eq(properties.getTimeoutMs()));
    }

    @Test
    public void shouldSendAllBidsToLiveIntent() {
        // given
        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(httpClient.post(anyString(), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        target.processEvent(buildEvent(true));

        // then
        verify(httpClient).post(
                eq(properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-bids"),
                jsonCaptor.capture(),
                eq(properties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final List<PbsjBid> pbsjBids = jacksonMapper.decodeValue(capturedJson, pbjsCollectionType);
        assertThat(pbsjBids).isEqualTo(List.of(
                PbsjBid.builder()
                        .bidId("bid-id")
                        .price(BigDecimal.ONE)
                        .adUnitId("ad-unit-id")
                        .enriched(true)
                        .currency("USD")
                        .treatmentRate(0.5f)
                        .timestamp(0L)
                        .partnerId("pbsj")
                        .build()));
    }

    @Test
    public void shouldSendAllBidsToLiveIntentNotEnriched() {
        // given
        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(httpClient.post(anyString(), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        target.processEvent(buildEvent(false));

        // then
        verify(httpClient).post(
                eq(properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-bids"),
                jsonCaptor.capture(),
                eq(properties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final List<PbsjBid> pbsjBids = jacksonMapper.decodeValue(capturedJson, pbjsCollectionType);
        assertThat(pbsjBids).isEqualTo(List.of(
                PbsjBid.builder()
                        .bidId("bid-id")
                        .price(BigDecimal.ONE)
                        .adUnitId("ad-unit-id")
                        .enriched(false)
                        .currency("USD")
                        .treatmentRate(0.5f)
                        .timestamp(0L)
                        .partnerId("pbsj")
                        .build()));
    }

    @Test
    public void shouldSendAllBidsToLiveIntentNoTreatmentRate() {
        // given
        final HttpClientResponse mockResponse = mock(HttpClientResponse.class);
        when(httpClient.post(anyString(), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(mockResponse));

        // when
        target.processEvent(buildEvent(false, false));

        // then
        verify(httpClient).post(
                eq(properties.getAnalyticsEndpoint() + "/analytic-events/pbsj-bids"),
                jsonCaptor.capture(),
                eq(properties.getTimeoutMs()));

        final String capturedJson = jsonCaptor.getValue();
        final List<PbsjBid> pbsjBids = jacksonMapper.decodeValue(capturedJson, pbjsCollectionType);
        assertThat(pbsjBids).isEqualTo(List.of(
                PbsjBid.builder()
                        .bidId("bid-id")
                        .price(BigDecimal.ONE)
                        .adUnitId("ad-unit-id")
                        .enriched(false)
                        .currency("USD")
                        .timestamp(0L)
                        .treatmentRate(null)
                        .partnerId("pbsj")
                        .build()));
    }

    private AuctionEvent buildEvent(Boolean isEnriched) {
        return buildEvent(isEnriched, true);
    }

    private AuctionEvent buildEvent(Boolean isEnriched, Boolean withTags) {
        final HookId hookId = HookId.of(
                "liveintent-omni-channel-identity-enrichment-hook",
                "liveintent-omni-channel-identity-enrichment-hook");

        final ActivityImpl enrichmentRate = ActivityImpl.of("liveintent-treatment-rate", "0.5", List.of());

        final List<ActivityImpl> enriched = isEnriched
                ? List.of(ActivityImpl.of("liveintent-enriched", "success", List.of()))
                : List.of();

        final HookExecutionOutcome hookExecutionOutcome = HookExecutionOutcome.builder()
                .hookId(hookId)
                .executionTime(100L)
                .status(ExecutionStatus.success)
                .analyticsTags(TagsImpl.of(
                        withTags
                                ? ListUtil.union(List.of(enrichmentRate), enriched)
                                : List.of()))
                .action(null)
                .build();

        final StageExecutionOutcome stageExecutionOutcome = StageExecutionOutcome.of(
                "auction-request",
                List.of(GroupExecutionOutcome.of(List.of(hookExecutionOutcome))));

        final EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes = new EnumMap<>(Stage.class);
        stageOutcomes.put(Stage.processed_auction_request, List.of(stageExecutionOutcome));
        return AuctionEvent.builder()
                .auctionContext(
                        AuctionContext.builder()
                                .bidRequest(BidRequest.builder()
                                        .id("request-id")
                                        .imp(List.of(
                                                Imp.builder()
                                                        .id("imp-id")
                                                        .tagid("ad-unit-id")
                                                        .build()))
                                        .build())
                                .bidResponse(BidResponse.builder()
                                        .bidid("bid-id")
                                        .cur("USD")
                                        .seatbid(List.of(
                                                SeatBid.builder()
                                                        .bid(List.of(
                                                                Bid.builder()
                                                                        .id("bid-id")
                                                                        .impid("imp-id")
                                                                        .price(BigDecimal.ONE)
                                                                        .build()))
                                                        .build()))
                                        .build())
                                .hookExecutionContext(HookExecutionContext.of(
                                        Endpoint.openrtb2_auction,
                                        stageOutcomes))
                                .build())
                .build();
    }
}
