package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredBidResponse;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

public class StoredResponseProcessorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    private StoredResponseProcessor storedResponseProcessor;

    private Timeout timeout;

    @Before
    public void setUp() {
        final TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        timeout = timeoutFactory.create(500L);

        storedResponseProcessor = new StoredResponseProcessor(applicationSettings, jacksonMapper);
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForAuctionResponseId() throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(givenImp("impId", ExtStoredAuctionResponse.of("1"), null));

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(singletonMap("1",
                                mapper.writeValueAsString(singletonList(SeatBid.builder().seat("rubicon")
                                        .bid(singletonList(Bid.builder().id("id").build())).build()))),
                        emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                emptyList(),
                singletonList(SeatBid.builder()
                        .seat("rubicon")
                        .bid(singletonList(Bid.builder().id("id").impid("impId").build()))
                        .build()),
                emptyMap()));
    }

    @Test
    public void getStoredResponseResultShouldNotChangeImpsAndReturnSeatBidsWhenThereAreNoStoredIds() {
        // given
        final Imp imp = Imp.builder()
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().bidder(mapper.createObjectNode().put("rubicon", 1)).build(),
                        null)))
                .build();
        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(singletonList(imp), timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(imp),
                emptyList(),
                emptyMap()));
        verifyNoInteractions(applicationSettings);
    }

    @Test
    public void getStoredResponseResultShouldAddImpToRequiredRequestWhenItsStoredAuctionResponseIsNull() {
        // given
        final List<Imp> imps = singletonList(givenImp("impId1", null, null));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(Imp.builder()
                        .id("impId1")
                        .ext(mapper.valueToTree(ExtImp.of(
                                ExtImpPrebid.builder().storedAuctionResponse(null).build(),
                                null)))
                        .build()),
                emptyList(),
                emptyMap()));
        verifyNoInteractions(applicationSettings);
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenErrorHappenedDuringRetrievingStoredResponse() {
        // given
        final List<Imp> imps = singletonList(givenImp("impId", ExtStoredAuctionResponse.of("1"), null));

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Failed.")));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stored response fetching failed with reason: Failed.");
    }

    @Test
    public void getStoredResponseResultShouldReturnResultForBidStoredResponseId() {
        // given
        final Imp imp = givenImp("impId1", null, asList(
                ExtStoredBidResponse.of("rubicon", "storedBidResponseId1"),
                ExtStoredBidResponse.of("appnexus", "storedBidResponseId2")));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(
                        doubleMap("storedBidResponseId1", "storedBidResponse1",
                                "storedBidResponseId2", "storedBidResponse2"), emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(singletonList(imp), timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(imp),
                emptyList(),
                singletonMap("impId1", doubleMap("rubicon", "storedBidResponse1", "appnexus", "storedBidResponse2"))));
    }

    @Test
    public void getStoredResponseResultShouldReturnResultForBidAndAuctionStoredResponseId()
            throws JsonProcessingException {
        // given
        final Imp imp1 = givenImp("impId1", ExtStoredAuctionResponse.of("storedAuctionResponseId"), null);
        final Imp imp2 = givenImp("impId2", null,
                singletonList(ExtStoredBidResponse.of("rubicon", "storedBidResponseId")));
        final List<Imp> imps = asList(imp1, imp2);

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedAuctionResponseId", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1").build())).build())));
        storedResponse.put("storedBidResponseId", "storedBidResponse");

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(imp2),
                singletonList(
                        SeatBid.builder()
                                .seat("appnexus")
                                .bid(singletonList(Bid.builder().id("id1").impid("impId1").build()))
                                .build()),
                singletonMap("impId2", singletonMap("rubicon", "storedBidResponse"))));
    }

    @Test
    public void getStoredResponseResultShouldThrowInvalidRequestExceptionWhenStoredAuctionResponseWasNotFound() {
        // given
        final Imp imp1 = givenImp("impId1", ExtStoredAuctionResponse.of("storedAuctionResponseId"), null);
        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(emptyMap(), emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(singletonList(imp1), timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class)
                .hasMessage("Failed to fetch stored auction response for impId = impId1 and storedAuctionResponse id "
                        + "= storedAuctionResponseId.");
    }

    @Test
    public void getStoredResponseResultShouldMergeStoredSeatBidsForTheSameBidder() throws JsonProcessingException {
        // given
        final List<Imp> imps = asList(
                givenImp("impId1", ExtStoredAuctionResponse.of("storedAuctionResponse1"), null),
                givenImp("impId2", ExtStoredAuctionResponse.of("storedAuctionResponse2"), null));

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedAuctionResponse1", mapper.writeValueAsString(asList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1").build())).build(),
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id3").build())).build())));
        storedResponse.put("storedAuctionResponse2", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id2").build())).build())));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                emptyList(),
                asList(
                        SeatBid.builder()
                                .seat("appnexus")
                                .bid(singletonList(Bid.builder().id("id1").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .seat("rubicon")
                                .bid(asList(
                                        Bid.builder().id("id2").impid("impId2").build(),
                                        Bid.builder().id("id3").impid("impId1").build()
                                ))
                                .build()),
                emptyMap()));
    }

    @Test
    public void getStoredResponseResultShouldThrowInvalidExceptionWhenImpExtIsNotValid() {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .id("impId")
                .ext(mapper.createObjectNode().put("prebid", 5))
                .build());

        // when and then
        assertThatThrownBy(() -> storedResponseProcessor.getStoredResponseResult(imps, timeout))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageStartingWith("Error decoding bidRequest.imp.ext for impId = impId :");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenSeatIsEmptyInStoredSeatBid()
            throws JsonProcessingException {

        // given
        final List<Imp> imps = singletonList(givenImp("impId", ExtStoredAuctionResponse.of("1"), null));

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(
                        singletonMap(
                                "1",
                                mapper.writeValueAsString(singletonList(SeatBid.builder()
                                        .bid(singletonList(Bid.builder().id("id").build()))
                                        .build()))),
                        emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Seat can't be empty in stored response seatBid");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenBidsAreEmptyInStoredSeatBid()
            throws JsonProcessingException {

        // given
        final List<Imp> imps = singletonList(
                givenImp("impId", ExtStoredAuctionResponse.of("1"), null));

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(
                        singletonMap(
                                "1",
                                mapper.writeValueAsString(singletonList(SeatBid.builder()
                                        .seat("seat")
                                        .build()))),
                        emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("There must be at least one bid in stored response seatBid");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureSeatBidsCannotBeParsed() {
        // given
        final List<Imp> imps = singletonList(givenImp("impId", ExtStoredAuctionResponse.of("1"), null));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(Future.succeededFuture(
                StoredResponseDataResult.of(singletonMap("1", "{invalid"), emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Can't parse Json for stored response with id 1");
    }

    @Test
    public void updateStoredBidResponseShouldTolerateMissingBidImpId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("rubicon", null, "storedresponse", bidRequest);

        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                100);

        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderRequest(bidderRequest)
                .bidderResponse(bidderResponse)
                .build();

        // when
        final List<AuctionParticipation> result = storedResponseProcessor
                .updateStoredBidResponse(singletonList(requestAuctionParticipation));

        // then
        assertThat(result).containsExactly(requestAuctionParticipation);
    }

    @Test
    public void updateStoredBidResponseShouldNotModifyParticipationWithMoreThanOneImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder().build(), Imp.builder().build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("rubicon", null, "storedresponse", bidRequest);

        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                100);

        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderRequest(bidderRequest)
                .bidderResponse(bidderResponse)
                .build();

        // when
        final List<AuctionParticipation> result = storedResponseProcessor
                .updateStoredBidResponse(singletonList(requestAuctionParticipation));

        // then
        assertThat(result).containsExactly(requestAuctionParticipation);
    }

    @Test
    public void updateStoredBidResponseShouldReplaceAllBidImpIdMacrosForStoredResponseParticipation() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").build()))
                .build();
        final BidderRequest bidderRequest = BidderRequest.of("rubicon", null, "storedresponse", bidRequest);

        final List<BidderBid> bids = List.of(
                BidderBid.of(Bid.builder().impid("##PBSIMPID##").build(), BidType.banner, "USD"),
                BidderBid.of(Bid.builder().impid("##PBSIMPID##").build(), BidType.video, "USD"));
        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon", BidderSeatBid.of(bids), 100);

        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderRequest(bidderRequest)
                .bidderResponse(bidderResponse)
                .build();

        // when
        final List<AuctionParticipation> result = storedResponseProcessor
                .updateStoredBidResponse(singletonList(requestAuctionParticipation));

        // then
        final List<BidderBid> expectedBids = List.of(
                BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, "USD"),
                BidderBid.of(Bid.builder().impid("impId").build(), BidType.video, "USD"));
        final BidderResponse expectedBidderResponse = BidderResponse.of(
                "rubicon", BidderSeatBid.of(expectedBids), 100);

        assertThat(result).containsExactly(requestAuctionParticipation.with(expectedBidderResponse));
    }

    @Test
    public void mergeWithBidderResponsesShouldReturnMergedStoredSeatWithResponseWithoutBlocked() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                100);
        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderResponse(bidderResponse)
                .build();
        final AuctionParticipation blockedRequestAuctionParticipation = AuctionParticipation.builder()
                .bidder("appnexus")
                .requestBlocked(true)
                .build();
        final List<AuctionParticipation> auctionParticipations = Arrays.asList(requestAuctionParticipation,
                blockedRequestAuctionParticipation);

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<AuctionParticipation> result = storedResponseProcessor.mergeWithBidderResponses(
                auctionParticipations, seatBid, imps);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .containsOnly(BidderResponse.of(
                                "rubicon",
                                BidderSeatBid.of(
                                        asList(
                                                BidderBid.of(
                                                        Bid.builder()
                                                                .id("bid2")
                                                                .impid("storedImp")
                                                                .build(),
                                                        BidType.banner,
                                                        "USD"),
                                                BidderBid.of(
                                                        Bid.builder()
                                                                .id("bid1")
                                                                .build(),
                                                        BidType.banner,
                                                        "USD"))),
                                100),
                        null);
    }

    @Test
    public void mergeWithBidderResponsesShouldMergeBidderResponsesWithoutCorrespondingStoredSeatBid() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                100);
        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderResponse(bidderResponse)
                .build();
        final List<AuctionParticipation> auctionParticipations = singletonList(requestAuctionParticipation);

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("appnexus")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<AuctionParticipation> result = storedResponseProcessor.mergeWithBidderResponses(
                auctionParticipations, seatBid, imps);

        // then
        final BidderResponse secondExpectedBidResponse = BidderResponse.of(
                "appnexus",
                BidderSeatBid.of(singletonList(
                        BidderBid.of(Bid.builder().id("bid2").impid("storedImp").build(), BidType.banner, "USD"))),
                0);
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .contains(bidderResponse, secondExpectedBidResponse);
    }

    @Test
    public void mergeWithBidderResponsesShouldMergeStoredSeatBidsWithoutBidderResponses() {
        // given
        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<AuctionParticipation> result =
                storedResponseProcessor.mergeWithBidderResponses(emptyList(), seatBid, imps);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .contains(BidderResponse.of(
                        "rubicon",
                        BidderSeatBid.of(
                                singletonList(BidderBid.of(
                                        Bid.builder().id("bid2").impid("storedImp").build(),
                                        BidType.banner,
                                        "USD"))),
                        0));
    }

    @Test
    public void mergeWithBidderResponsesShouldResolveCurrencyFromBidderResponse() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "EUR"))),
                100);
        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderResponse(bidderResponse)
                .build();
        final List<AuctionParticipation> auctionParticipations = singletonList(requestAuctionParticipation);

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<AuctionParticipation> result = storedResponseProcessor.mergeWithBidderResponses(
                auctionParticipations, seatBid, imps);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .contains(BidderResponse.of(
                        "rubicon",
                        BidderSeatBid.of(
                                asList(
                                        BidderBid.of(
                                                Bid.builder().id("bid2").impid("storedImp").build(),
                                                BidType.banner,
                                                "EUR"),
                                        BidderBid.of(
                                                Bid.builder().id("bid1").build(),
                                                BidType.banner,
                                                "EUR"))),

                        100));
    }

    @Test
    public void mergeWithBidderResponsesShouldResolveBidTypeFromStoredBidExt() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                100);
        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderResponse(bidderResponse)
                .build();
        final List<AuctionParticipation> auctionParticipations = singletonList(requestAuctionParticipation);

        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().type(BidType.video).build();

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder()
                        .id("bid2")
                        .impid("storedImp")
                        .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(extBidPrebid)))
                        .build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<AuctionParticipation> result = storedResponseProcessor.mergeWithBidderResponses(
                auctionParticipations, seatBid, imps);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .contains(BidderResponse.of(
                        "rubicon",
                        BidderSeatBid.of(asList(
                                BidderBid.of(
                                        Bid.builder()
                                                .id("bid2")
                                                .impid("storedImp")
                                                .ext(mapper.createObjectNode()
                                                        .set("prebid", mapper.valueToTree(extBidPrebid))).build(),
                                        BidType.video, "USD"),
                                BidderBid.of(
                                        Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                        100));
    }

    @Test
    public void mergeWithBidderResponsesShouldThrowPrebidExceptionWhenExtBidPrebidInStoredBidIsNotValid() {
        // given
        final ObjectNode extBidPrebid = mapper.createObjectNode().put("type", "invalid");

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder()
                        .id("bid2")
                        .impid("storedImp")
                        .ext(mapper.createObjectNode().set("prebid", extBidPrebid))
                        .build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when and then
        assertThatThrownBy(() -> storedResponseProcessor.mergeWithBidderResponses(emptyList(), seatBid, imps))
                .isInstanceOf(PreBidException.class).hasMessage("Error decoding stored response bid.ext.prebid");
    }

    @Test
    public void mergeWithBidderResponsesShouldReturnSameResponseWhenThereAreNoStoredResponses() {
        // given
        final BidderResponse bidderResponse = BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD"))),
                100);
        final AuctionParticipation requestAuctionParticipation = AuctionParticipation.builder()
                .bidder("rubicon")
                .bidderResponse(bidderResponse)
                .build();
        final List<AuctionParticipation> auctionParticipations = singletonList(requestAuctionParticipation);

        final List<Imp> imps = singletonList(Imp.builder().banner(Banner.builder().build()).build());

        // when
        final List<AuctionParticipation> result = storedResponseProcessor.mergeWithBidderResponses(
                auctionParticipations, emptyList(), imps);

        // then
        assertThat(result)
                .extracting(AuctionParticipation::getBidderResponse)
                .containsOnly(bidderResponse);
    }

    private <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private Imp givenImp(String impId,
                         ExtStoredAuctionResponse storedAuctionResponse,
                         List<ExtStoredBidResponse> extStoredBidResponse) {
        return Imp.builder()
                .id(impId)
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .storedAuctionResponse(storedAuctionResponse)
                                .storedBidResponse(extStoredBidResponse)
                                .build(),
                        null)))
                .build();
    }
}
