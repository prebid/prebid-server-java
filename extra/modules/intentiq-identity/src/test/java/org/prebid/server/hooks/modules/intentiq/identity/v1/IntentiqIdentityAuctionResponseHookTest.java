package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.IntentiqIdentityModuleContext;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class IntentiqIdentityAuctionResponseHookTest {

    private static final JacksonMapper MAPPER = new JacksonMapper(ObjectMapperProvider.mapper());
    private static final JsonMerger MERGER = new JsonMerger(MAPPER);
    private static final String REPORTS = "https://reports.example.com/report";

    @Mock
    private HttpClient httpClient;

    @Mock
    private AuctionInvocationContext invocationContext;

    @Mock
    private AuctionContext auctionContext;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final IntentiqIdentityMetrics metrics = new IntentiqIdentityMetrics(metricRegistry);

    @BeforeEach
    public void setUp() {
        when(invocationContext.accountConfig()).thenReturn(MAPPER.mapper().createObjectNode());
        when(invocationContext.auctionContext()).thenReturn(auctionContext);
        when(invocationContext.moduleContext()).thenReturn(null);
        when(httpClient.get(urlCaptor.capture(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{}")));
    }

    private IntentiqIdentityAuctionResponseHook target(String reportsEndpoint, String partnerId) {
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setReportsEndpoint(reportsEndpoint);
        properties.setPartnerId(partnerId);
        properties.setTimeout(1000L);
        final ConfigResolver configResolver = new ConfigResolver(MAPPER.mapper(), MERGER, properties);
        return new IntentiqIdentityAuctionResponseHook(configResolver, httpClient, MAPPER, metrics);
    }

    private AuctionResponsePayload payload(BidResponse bidResponse) {
        return AuctionResponsePayloadImpl.of(bidResponse);
    }

    private void givenRequest(BidRequest bidRequest) {
        when(auctionContext.getBidRequest()).thenReturn(bidRequest);
    }

    private static BidResponse response(String seat, Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder().seat(seat).bid(List.of(bids)).build()))
                .build();
    }

    private static String rdataJson(String url) {
        final String marker = "rdata=";
        final String encoded = url.substring(url.indexOf(marker) + marker.length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    @Test
    public void callShouldReportWinningBidWithRdataAndConstants() {
        // given
        givenRequest(BidRequest.builder()
                .site(Site.builder().domain("test.com").build())
                .device(Device.builder().ip("1.2.3.4").ua("UA").build())
                .build());
        when(invocationContext.moduleContext())
                .thenReturn(new IntentiqIdentityModuleContext(System.nanoTime(), "ab-123", null));
        final Bid bid = Bid.builder().impid("imp1").price(BigDecimal.valueOf(1.18)).build();

        // when
        target(REPORTS, "999").call(payload(response("pubmatic", bid)), invocationContext);

        // then
        final String url = urlCaptor.getValue();
        assertThat(url).startsWith(REPORTS + "?at=45&rtype=1&source=pbjv&dpi=999&rdata=");
        assertThat(url).contains("pubmatic").contains("imp1").contains("ab-123").contains("test.com");
        assertThat(metricRegistry.counter("modules.module.intentiq-identity.custom.impression.reported_999")
                .getCount())
                .isEqualTo(1);
    }

    @Test
    public void callShouldReportEachWinningBid() {
        // given
        givenRequest(BidRequest.builder().build());
        final Bid bid1 = Bid.builder().impid("a").price(BigDecimal.ONE).build();
        final Bid bid2 = Bid.builder().impid("b").price(BigDecimal.TEN).build();

        // when
        target(REPORTS, "999").call(payload(response("seat", bid1, bid2)), invocationContext);

        // then
        verify(httpClient, times(2)).get(any(), any(), anyLong());
    }

    @Test
    public void callShouldOmitAbTestUuidWhenAbsent() {
        // given
        givenRequest(BidRequest.builder().build());
        final Bid bid = Bid.builder().impid("a").price(BigDecimal.ONE).build();

        // when
        target(REPORTS, "999").call(payload(response("seat", bid)), invocationContext);

        // then
        assertThat(urlCaptor.getValue()).doesNotContain("abTestUuid");
    }

    @Test
    public void callShouldIncludeOriginalCpmAndCurrencyFromBidExt() {
        // given
        givenRequest(BidRequest.builder().build());
        final ObjectNode ext = MAPPER.mapper().createObjectNode();
        ext.put("origbidcpm", new BigDecimal("2.50"));
        ext.put("origbidcur", "EUR");
        final Bid bid = Bid.builder().impid("a").price(BigDecimal.valueOf(1.18)).ext(ext).build();

        // when
        target(REPORTS, "999").call(payload(response("seat", bid)), invocationContext);

        // then
        final String rdata = rdataJson(urlCaptor.getValue());
        assertThat(rdata).contains("\"originalCpm\":2.50").contains("\"originalCurrency\":\"EUR\"");
    }

    @Test
    public void callShouldIncludePartnerAuctionIdFromRequestId() {
        // given
        givenRequest(BidRequest.builder().id("auction-77").build());
        final Bid bid = Bid.builder().impid("a").price(BigDecimal.ONE).build();

        // when
        target(REPORTS, "999").call(payload(response("seat", bid)), invocationContext);

        // then
        assertThat(rdataJson(urlCaptor.getValue())).contains("\"partnerAuctionId\":\"auction-77\"");
    }

    @Test
    public void callShouldIncludeTerminationCauseFromModuleContext() {
        // given
        givenRequest(BidRequest.builder().build());
        when(invocationContext.moduleContext())
                .thenReturn(new IntentiqIdentityModuleContext(System.nanoTime(), "ab-1", 7L));
        final Bid bid = Bid.builder().impid("a").price(BigDecimal.ONE).build();

        // when
        target(REPORTS, "999").call(payload(response("seat", bid)), invocationContext);

        // then
        assertThat(rdataJson(urlCaptor.getValue())).contains("\"terminationCause\":7");
    }

    @Test
    public void callShouldOmitTerminationCauseWhenModuleContextHasNone() {
        // given
        givenRequest(BidRequest.builder().build());
        when(invocationContext.moduleContext())
                .thenReturn(new IntentiqIdentityModuleContext(System.nanoTime(), "ab-1", null));
        final Bid bid = Bid.builder().impid("a").price(BigDecimal.ONE).build();

        // when
        target(REPORTS, "999").call(payload(response("seat", bid)), invocationContext);

        // then
        assertThat(rdataJson(urlCaptor.getValue())).doesNotContain("terminationCause");
    }

    @Test
    public void callShouldReturnNoActionAndSkipReportingWhenReportsEndpointBlank() {
        // when
        final var result = target(null, "999")
                .call(payload(response("seat", Bid.builder().impid("a").price(BigDecimal.ONE).build())),
                        invocationContext)
                .result();

        // then
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        verifyNoInteractions(httpClient);
    }
}
