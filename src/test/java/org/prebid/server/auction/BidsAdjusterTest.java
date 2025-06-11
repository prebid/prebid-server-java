package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.adpodding.AdPoddingBidDeduplicationService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidadjustments.BidAdjustmentsProcessor;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

@ExtendWith(MockitoExtension.class)
public class BidsAdjusterTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private ResponseBidValidator responseBidValidator;

    @Mock(strictness = LENIENT)
    private PriceFloorEnforcer priceFloorEnforcer;

    @Mock(strictness = LENIENT)
    private DsaEnforcer dsaEnforcer;

    @Mock(strictness = LENIENT)
    private BidAdjustmentsProcessor bidAdjustmentsProcessor;

    @Mock(strictness = LENIENT)
    private AdPoddingBidDeduplicationService adPoddingBidDeduplicationService;

    private BidsAdjuster target;

    @BeforeEach
    public void setUp() {
        given(responseBidValidator.validate(any(), any(), any(), any())).willReturn(ValidationResult.success());

        given(priceFloorEnforcer.enforce(any(), any(), any(), any())).willAnswer(inv -> inv.getArgument(1));
        given(dsaEnforcer.enforce(any(), any(), any())).willAnswer(inv -> inv.getArgument(1));
        given(bidAdjustmentsProcessor.enrichWithAdjustedBids(any(), any()))
                .willAnswer(inv -> inv.getArgument(0));
        given(adPoddingBidDeduplicationService.deduplicate(any(), any(), any(), any()))
                .willAnswer(inv -> inv.getArgument(1));

        target = new BidsAdjuster(
                responseBidValidator,
                priceFloorEnforcer,
                bidAdjustmentsProcessor,
                dsaEnforcer,
                adPoddingBidDeduplicationService);
    }

    @Test
    public void shouldReturnBidsAdjustedByBidAdjustmentsProcessor() {
        // given
        final BidderBid bidToAdjust =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE).build(), "USD");
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder().bids(List.of(bidToAdjust)).build(),
                1);

        final BidRequest bidRequest = givenBidRequest(
                List.of(givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                identity());

        final BidderBid adjustedBid =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.TEN).build(), "USD");

        given(bidAdjustmentsProcessor.enrichWithAdjustedBids(any(), any()))
                .willReturn(AuctionParticipation.builder()
                        .bidder("bidder1")
                        .bidderResponse(BidderResponse.of(
                                "bidder1", BidderSeatBid.of(singletonList(adjustedBid)), 0))
                        .build());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target.validateAndAdjustBids(
                auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(adjustedBid);
    }

    @Test
    public void shouldReturnBidsAcceptedByPriceFloorEnforcer() {
        // given
        final BidderBid bidToAccept =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE).build(), "USD");
        final BidderBid bidToReject =
                givenBidderBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.TEN).build(), "USD");
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(bidToAccept, bidToReject))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(List.of(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId2"))),
                identity());

        given(priceFloorEnforcer.enforce(any(), any(), any(), any()))
                .willReturn(AuctionParticipation.builder()
                        .bidder("bidder1")
                        .bidderResponse(BidderResponse.of(
                                "bidder1", BidderSeatBid.of(singletonList(bidToAccept)), 0))
                        .build());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target.validateAndAdjustBids(
                auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(bidToAccept);
    }

    @Test
    public void shouldReturnBidsAcceptedByDsaEnforcer() {
        // given
        final BidderBid bidToAccept =
                givenBidderBid(Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE).build(), "USD");
        final BidderBid bidToReject =
                givenBidderBid(Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.TEN).build(), "USD");

        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(bidToAccept, bidToReject))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(List.of(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId2"))),
                identity());

        given(dsaEnforcer.enforce(any(), any(), any()))
                .willReturn(AuctionParticipation.builder()
                        .bidder("bidder1")
                        .bidderResponse(BidderResponse.of(
                                "bidder1", BidderSeatBid.of(singletonList(bidToAccept)), 0))
                        .build());

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .containsExactly(bidToAccept);
    }

    @Test
    public void shouldTolerateResponseBidValidationErrors() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(givenBidderBid(
                                Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build())))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        when(responseBidValidator.validate(any(), any(), any(), any()))
                .thenReturn(ValidationResult.error("Error: bid validation error."));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .isEmpty();
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(
                        BidderError.invalidBid(
                                "BidId `bidId1` validation messages: Error: Error: bid validation error."));
    }

    @Test
    public void shouldTolerateResponseBidValidationWarnings() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(givenBidderBid(
                                Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(1.23)).build())))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(singletonList(
                        // imp ids are not really used for matching, included them here for clarity
                        givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .build())));

        when(responseBidValidator.validate(any(), any(), any(), any()))
                .thenReturn(ValidationResult.warning(singletonList("Error: bid validation warning.")));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .hasSize(1);
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getErrors)
                .containsOnly(BidderError.invalidBid(
                        "BidId `bidId1` validation messages: Warning: Error: bid validation warning."));
    }

    @Test
    public void shouldAddWarningAboutMultipleCurrency() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "bidder",
                BidderSeatBid.builder()
                        .bids(List.of(
                                givenBidderBid(Bid.builder().impid("impId1").price(BigDecimal.valueOf(2.0)).build(),
                                        "CUR1")))
                        .build(),
                1);

        final BidRequest bidRequest = givenBidRequest(
                singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.cur(List.of("CUR1", "CUR2", "CUR2")));

        final List<AuctionParticipation> auctionParticipations = givenAuctionParticipation(bidderResponse, bidRequest);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        // when
        final List<AuctionParticipation> result = target
                .validateAndAdjustBids(auctionParticipations, auctionContext, null);

        // then
        assertThat(result).hasSize(1);

        final BidderSeatBid firstSeatBid = result.getFirst().getBidderResponse().getSeatBid();
        final BidderError expectedWarning = BidderError.badInput(
                "a single currency (CUR1) has been chosen for the request. "
                        + "ORTB 2.6 requires that all responses are in the same currency.");
        assertThat(firstSeatBid.getWarnings()).containsOnly(expectedWarning);
    }

    private List<AuctionParticipation> givenAuctionParticipation(
            BidderResponse bidderResponse, BidRequest bidRequest) {

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidRequest(bidRequest)
                .build();

        return List.of(AuctionParticipation.builder()
                .bidder("bidder")
                .bidderRequest(bidderRequest)
                .bidderResponse(bidderResponse)
                .build());
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return AuctionContext.builder()
                .bidRequest(bidRequest)
                .bidRejectionTrackers(Map.of("bidder", new BidRejectionTracker("bidder", Set.of(), 1)))
                .build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp,
                                              UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {

        return bidRequestBuilderCustomizer
                .apply(BidRequest.builder().cur(singletonList("USD")).imp(imp).tmax(500L))
                .build();
    }

    private static <T> Imp givenImp(T ext, UnaryOperator<Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()
                        .id(UUID.randomUUID().toString())
                        .ext(mapper.valueToTree(singletonMap(
                                "prebid", ext != null ? singletonMap("bidder", ext) : emptyMap()))))
                .build();
    }

    private static BidderBid givenBidderBid(Bid bid) {
        return BidderBid.of(bid, banner, null);
    }

    private static BidderBid givenBidderBid(Bid bid, String currency) {
        return BidderBid.of(bid, banner, currency);
    }
}
