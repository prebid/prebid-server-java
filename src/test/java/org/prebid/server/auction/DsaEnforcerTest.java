package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.request.DsaPublisherRender;
import org.prebid.server.proto.openrtb.ext.request.DsaRequired;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.DsaAdvertiserRender;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class DsaEnforcerTest extends VertxTest {

    private final DsaEnforcer target = new DsaEnforcer(jacksonMapper);

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
    public void enforceShouldDoNothingWhenDsaIsNotRequiredAndDsaResponseIsMissing() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.NOT_REQUIRED, DsaPublisherRender.NOT_RENDER);
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
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsNotRequiredAndDsaResponseHasInvalidBehalfField() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.NOT_REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();
        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", RandomStringUtils.randomAlphabetic(101))
                .put("paid", RandomStringUtils.randomAlphabetic(100))
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\": DSA behalf exceeds limit of 100 chars")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsNotRequiredAndDsaResponseHasInvalidPaidField() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.NOT_REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();
        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", RandomStringUtils.randomAlphabetic(100))
                .put("paid", RandomStringUtils.randomAlphabetic(101))
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\": DSA paid exceeds limit of 100 chars")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherNotRenderAndAdvertiserRenders() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.WILL_RENDER.getValue());
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
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherRendersAndAdvertiserNotRender() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.WILL_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
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
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherRenderIsAbsentAndAdvertiserNotRender() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, null);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
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
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherCouldRenderAndAdvertiserNotRender() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.COULD_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
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
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherCouldRenderAndAdvertiserRenders() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.COULD_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.WILL_RENDER.getValue());
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
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherCouldRenderAndAdvertiserIsAbsent() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.COULD_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser");
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
    public void enforceDoNothingWhenDsaIsRequiredAndPublisherRendersAndAdvertiserRenderIsAbsent() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.WILL_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser");
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
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.NOT_RENDER);
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
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\": DSA object missing when required")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsRequiredAndDsaResponseHasInvalidPaidField() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", RandomStringUtils.randomAlphabetic(100))
                .put("paid", RandomStringUtils.randomAlphabetic(101))
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\": DSA paid exceeds limit of 100 chars")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsRequiredAndDsaResponseHasInvalidBehalfField() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", RandomStringUtils.randomAlphabetic(101))
                .put("paid", RandomStringUtils.randomAlphabetic(100))
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(BidderError.invalidBid("Bid \"bid_id\": DSA behalf exceeds limit of 100 chars")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsRequiredAndPublisherAndAdvertiserBothRender() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.WILL_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.WILL_RENDER.getValue());
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(
                        BidderError.invalidBid("Bid \"bid_id\": DSA publisher and buyer both signal will render")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsRequiredAndPublisherAndAdvertiserBothNotRender() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser")
                .put("adrender", DsaAdvertiserRender.NOT_RENDER.getValue());
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(
                        BidderError.invalidBid("Bid \"bid_id\": DSA publisher and buyer both signal will not render")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    @Test
    public void enforceShouldRejectBidAndAddWarningWhenDsaIsRequiredAndPublisherNotRenderAndAdvertiserIsAbsent() {
        // given
        final ExtRegs extRegs = givenExtRegs(DsaRequired.REQUIRED, DsaPublisherRender.NOT_RENDER);
        final BidRequest givenRequest = BidRequest.builder().regs(Regs.builder().ext(extRegs).build()).build();

        final ObjectNode dsaNode = mapper.createObjectNode()
                .put("behalf", "Advertiser")
                .put("paid", "Advertiser");
        final ObjectNode ext = mapper.createObjectNode().set("dsa", dsaNode);
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
                .warnings(List.of(
                        BidderError.invalidBid("Bid \"bid_id\": DSA publisher and buyer both signal will not render")))
                .bids(emptyList())
                .build();
        final AuctionParticipation expectedParticipation = AuctionParticipation.builder()
                .bidderResponse(BidderResponse.of("bidder", expectedSeatBid, 100))
                .build();
        assertThat(actual).isEqualTo(expectedParticipation);
        verify(bidRejectionTracker).reject("imp_id", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);
    }

    private static ExtRegs givenExtRegs(DsaRequired dsaRequired, DsaPublisherRender pubRender) {
        final ExtRegsDsa dsa = ExtRegsDsa.of(
                ObjectUtil.getIfNotNull(dsaRequired, DsaRequired::getValue),
                ObjectUtil.getIfNotNull(pubRender, DsaPublisherRender::getValue),
                3,
                emptyList());
        return ExtRegs.of(1, "usPrivacy", "1", dsa);
    }

}
