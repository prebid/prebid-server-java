package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.response.Bid;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class DsaEnforcerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    private final DsaEnforcer target = new DsaEnforcer();
    @Mock
    private BidRejectionTracker bidRejectionTracker;

    @Test
    public void enforceShouldDoNothingWhenBidsAreEmpty() {
        // given
        final BidRequest givenRequest = BidRequest.builder().build();
        final AuctionParticipation givenParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", BidderSeatBid.of(emptyList()), 100))
                .build();

        // when
        final AuctionParticipation actual = target.enforce(givenRequest, givenParticipation, bidRejectionTracker);

        // then
        assertThat(actual).isEqualTo(givenParticipation);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void enforceShouldDoNothingWhenDsaIsNotRequired() {
        // given
        final ExtRegs extRegs = ExtRegs.of(1, "usPrivacy", "1", ExtRegsDsa.of(1, 2, 3, emptyList()));
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();
        final BidderBid bid = BidderBid.of(Bid.builder().build(), BidType.banner, "USD");
        final AuctionParticipation givenParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", BidderSeatBid.of(List.of(bid)), 100))
                .build();

        // when
        final AuctionParticipation actual = target.enforce(givenRequest, givenParticipation, bidRejectionTracker);

        // then
        assertThat(actual).isEqualTo(givenParticipation);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void enforceDoNothingWhenDsaIsRequiredAndBidHasDsa() {
        final ExtRegs extRegs = ExtRegs.of(1, "usPrivacy", "1", ExtRegsDsa.of(2, 2, 3, emptyList()));
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", 1);
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
        final BidderBid bid = BidderBid.of(Bid.builder().ext(ext).build(), BidType.banner, "USD");
        final AuctionParticipation givenParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", BidderSeatBid.of(List.of(bid)), 100))
                .build();

        // when
        final AuctionParticipation actual = target.enforce(givenRequest, givenParticipation, bidRejectionTracker);

        // then
        assertThat(actual).isEqualTo(givenParticipation);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenBidExtHasEmptyDsaAndDsaIsRequired() {
        final ExtRegs extRegs = ExtRegs.of(1, "usPrivacy", "1", ExtRegsDsa.of(2, 2, 3, emptyList()));
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode ext = mapper.createObjectNode().set("dsa", mapper.createObjectNode());
        final BidderBid bid = BidderBid.of(
                Bid.builder().id("bid_id").impid("imp_id").ext(ext).build(),
                BidType.banner,
                "USD");
        final AuctionParticipation givenParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", BidderSeatBid.of(List.of(bid)), 100))
                .build();

        // when
        final AuctionParticipation actual = target.enforce(givenRequest, givenParticipation, bidRejectionTracker);

        // then
        final BidderSeatBid expectedSeatBid = BidderSeatBid.builder()
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\" missing DSA")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.GENERAL);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenBidExtIsEmptyDsaAndDsaIsRequired() {
        final ExtRegs extRegs = ExtRegs.of(1, "usPrivacy", "1", ExtRegsDsa.of(3, 2, 3, emptyList()));
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode ext = mapper.createObjectNode();
        final BidderBid bid = BidderBid.of(
                Bid.builder().id("bid_id").impid("imp_id").ext(ext).build(),
                BidType.banner,
                "USD");
        final AuctionParticipation givenParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", BidderSeatBid.of(List.of(bid)), 100))
                .build();

        // when
        final AuctionParticipation actual = target.enforce(givenRequest, givenParticipation, bidRejectionTracker);

        // then
        final BidderSeatBid expectedSeatBid = BidderSeatBid.builder()
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\" missing DSA")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.GENERAL);
    }

}
