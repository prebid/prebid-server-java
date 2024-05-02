package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.analytics.reporter.greenbids.GreenbidsAuctionContext.setupAuctionContext;
import static org.prebid.server.analytics.reporter.greenbids.GreenbidsAuctionContext.setupAuctionContextWithNoAdUnit;

public class GreenbidsAnalyticsReporterTest {

    ObjectMapper mapper = ObjectMapperProvider.mapper();
    JacksonMapper jacksonMapper = new JacksonMapper(mapper);
    GreenbidsAnalyticsProperties greenbidsAnalyticsProperties = GreenbidsAnalyticsProperties.builder()
            .pbuid("pbuid1")
            .greenbidsSampling(1.0)
            .exploratorySamplingSplit(0.9)
            .configurationRefreshDelayMs(10000L)
            .timeoutMs(100000L)
            .build();

    @Test
    public void shouldThrowExceptionWhenAdUnitsListIsEmpty() {
        // given
        final AuctionContext auctionContext = setupAuctionContextWithNoAdUnit();

        // when
        final GreenbidsAnalyticsReporter greenbidsAnalyticsReporter = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper
        );

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> greenbidsAnalyticsReporter.createBidMessage(
                        auctionContext,
                        auctionContext.getBidResponse()
                ),
                "AdUnits list should not be empty"
        );

    }

    @Test
    public void shouldConstructValidCommonMessage() {
        // given
        final AuctionContext auctionContext = setupAuctionContext();

        // when
        final GreenbidsAnalyticsReporter greenbidsAnalyticsReporter = new GreenbidsAnalyticsReporter(
                greenbidsAnalyticsProperties,
                jacksonMapper
        );

        final CommonMessage commonMessage = greenbidsAnalyticsReporter
                .createBidMessage(auctionContext, auctionContext.getBidResponse());

        // then
        assertThat(commonMessage).isNotNull();
        assertThat(commonMessage).hasFieldOrPropertyWithValue("pbuid", "pbuid1");

        for (AdUnit adUnit : commonMessage.adUnits) {
            assert adUnit.getBidders() != null;
            final boolean hasSeatWithBid = adUnit.getBidders().stream()
                    .anyMatch(bidder -> Boolean.TRUE.equals(bidder.getHasBid()));
            final boolean hasSeatWithNonBid = adUnit.getBidders().stream()
                    .anyMatch(bidder -> Boolean.FALSE.equals(bidder.getHasBid()));

            assertThat(hasSeatWithBid).isTrue();
            assertThat(hasSeatWithNonBid).isTrue();
        }
    }
}
