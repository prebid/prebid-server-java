package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class GreenbidsAnalyticsReporterTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    ObjectMapper mapper = ObjectMapperProvider.mapper();

    JacksonMapper jacksonMapper = new JacksonMapper(mapper);

    @Mock
    private HttpClient httpClient;

    GreenbidsAnalyticsProperties greenbidsAnalyticsProperties = GreenbidsAnalyticsProperties.builder()
            .pbuid("pbuid1")
            .greenbidsSampling(1.0)
            .exploratorySamplingSplit(0.9)
            .configurationRefreshDelayMs(10000L)
            .timeoutMs(100000L)
            .build();

    public static AuctionContext setupAuctionContext() {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
                .build();

        final Format format = Format.builder()
                .w(320)
                .h(50)
                .build();

        final Imp imp = Imp.builder()
                .id("imp1")
                .banner(
                        Banner.builder()
                                .format(Collections.singletonList(format))
                                .build())
                .tagid("tag1")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("request1")
                .imp(Collections.singletonList(imp))
                .site(site)
                .build();

        // bid response
        final Bid bid = Bid.builder()
                .id("bid1")
                .impid("imp1")
                .price(BigDecimal.valueOf(1.5))
                .adm("<div>Ad Markup</div>")
                .build();

        final SeatBid seatBidWithBid = SeatBid.builder()
                .bid(Collections.singletonList(bid))
                .seat("seat1")
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .id("response1")
                .seatbid(Collections.singletonList(seatBidWithBid))
                .cur("USD")
                .build();

        final BidRejectionTracker bidRejectionTracker = new BidRejectionTracker(
                "seat2",
                Set.of("imp1"),
                1.0);

        bidRejectionTracker.reject("imp1", BidRejectionReason.NO_BID);

        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest)
                .bidResponse(bidResponse)
                .bidRejectionTrackers(
                        Collections.singletonMap(
                                "seat2",
                                bidRejectionTracker))
                .build();
    }

    public static AuctionContext setupAuctionContextWithNoAdUnit() {
        // bid request
        final Site site = Site.builder()
                .domain("www.leparisien.fr")
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .id("request1")
                .site(site)
                .build();

        return AuctionContext.builder()
                .httpRequest(HttpRequestContext.builder().build())
                .bidRequest(bidRequest)
                .build();
    }

    @Test
    public void shouldThrowExceptionWhenAdUnitsListIsEmpty() {
        // given
        final AuctionContext auctionContext = setupAuctionContextWithNoAdUnit();
        final String greenbidsId = UUID.randomUUID().toString();
        final String billingId = UUID.randomUUID().toString();

        // when
        final GreenbidsAnalyticsReporter greenbidsAnalyticsReporter = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient);

        // then
        final Future<CommonMessage> bidMessage = greenbidsAnalyticsReporter.createBidMessage(
                auctionContext,
                auctionContext.getBidResponse(),
                greenbidsId,
                billingId);

        bidMessage.onComplete(ar -> {
            assertThat(ar.cause().getMessage()).isEqualTo("AdUnits list should not be empty");
        });
    }

    @Test
    public void shouldConstructValidCommonMessage() {
        // given
        final AuctionContext auctionContext = setupAuctionContext();
        final String greenbidsId = UUID.randomUUID().toString();
        final String billingId = UUID.randomUUID().toString();

        // when
        final GreenbidsAnalyticsReporter greenbidsAnalyticsReporter = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper,
                httpClient);

        final Future<CommonMessage> bidMessage = greenbidsAnalyticsReporter
                .createBidMessage(
                        auctionContext,
                        auctionContext.getBidResponse(),
                        greenbidsId,
                        billingId);

        // then
        bidMessage.onSuccess(commonMessage -> {

            assertThat(commonMessage).isNotNull();
            assertThat(commonMessage).hasFieldOrPropertyWithValue("pbuid", "pbuid1");

            for (AdUnit adUnit : commonMessage.getAdUnits()) {
                assert adUnit.getBidders() != null;
                final boolean hasSeatWithBid = adUnit.getBidders().stream()
                        .anyMatch(bidder -> Boolean.TRUE.equals(bidder.getHasBid()));
                final boolean hasSeatWithNonBid = adUnit.getBidders().stream()
                        .anyMatch(bidder -> Boolean.FALSE.equals(bidder.getHasBid()));

                assertThat(hasSeatWithBid).isTrue();
                assertThat(hasSeatWithNonBid).isTrue();
            }
        });
    }
}
