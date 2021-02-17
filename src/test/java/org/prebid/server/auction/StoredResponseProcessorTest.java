package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
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
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.BidderCatalog;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;

public class StoredResponseProcessorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private BidderCatalog bidderCatalog;

    private StoredResponseProcessor storedResponseProcessor;

    @Mock
    private BidderAliases aliases;
    private Timeout timeout;

    @Before
    public void setUp() {
        final TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        timeout = timeoutFactory.create(500L);

        storedResponseProcessor = new StoredResponseProcessor(applicationSettings, bidderCatalog, jacksonMapper);
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForAuctionResponseId() throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .id("impId")
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().storedAuctionResponse(ExtStoredAuctionResponse.of("1")).build(),
                        null)))
                .build());

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(singletonMap("1",
                        mapper.writeValueAsString(singletonList(SeatBid.builder().seat("rubicon")
                                .bid(singletonList(Bid.builder().id("id").build())).build()))),
                        emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                aliases, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                emptyList(),
                singletonList(SeatBid.builder()
                        .seat("rubicon")
                        .bid(singletonList(Bid.builder().id("id").impid("impId").build()))
                        .build())));
    }

    @Test
    public void getStoredResponseResultShouldNotChangeImpsAndReturnSeatBidsWhenThereAreNoStoredIds() {
        // given
        final Imp imp = Imp.builder()
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().bidder(mapper.createObjectNode().put("rubicon", 1)).build(),
                        null)))
                .build();

        given(bidderCatalog.isValidName(any())).willReturn(true);

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(singletonList(imp), aliases, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(imp),
                emptyList()));
        verifyZeroInteractions(applicationSettings);
    }

    @Test
    public void getStoredResponseResultShouldAddImpToRequiredRequestWhenItsStoredBidResponseIsEmpty() {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .id("impId1")
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().storedBidResponse(emptyList()).build(),
                        null)))
                .build());

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(Imp.builder()
                        .id("impId1")
                        .ext(mapper.valueToTree(ExtImp.of(
                                ExtImpPrebid.builder().storedBidResponse(emptyList()).build(),
                                null)))
                        .build()),
                emptyList()));
        verifyZeroInteractions(applicationSettings);
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenErrorHappenedDuringRetrievingStoredResponse() {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().storedAuctionResponse(ExtStoredAuctionResponse.of("1")).build(),
                        null)))
                .build());

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Failed.")));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stored response fetching failed with reason: Failed.");
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForBidStoredResponseId() throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .id("impId1")
                .ext(mapper.valueToTree(ExtImp.of(ExtImpPrebid.builder()
                                .storedBidResponse(asList(
                                        ExtStoredBidResponse.of("rubicon", "storedBidResponseId1"),
                                        ExtStoredBidResponse.of("appnexus", "storedBidResponseId2")))
                                .build(),
                        null)))
                .build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedBidResponseId1", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id1").build())).build())));
        storedResponse.put("storedBidResponseId2", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id2").build())).build())));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                emptyList(),
                asList(
                        SeatBid.builder()
                                .seat("appnexus")
                                .bid(singletonList(Bid.builder().id("id2").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .seat("rubicon")
                                .bid(singletonList(Bid.builder().id("id1").impid("impId1").build()))
                                .build())));
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForBidAndAuctionStoredResponseId()
            throws JsonProcessingException {

        // given
        final List<Imp> imps = asList(
                Imp.builder()
                        .id("impId1")
                        .ext(mapper.valueToTree(ExtImp.of(
                                ExtImpPrebid.builder()
                                        .storedAuctionResponse(ExtStoredAuctionResponse.of("storedAuctionRequest"))
                                        .build(),
                                null)))
                        .build(),
                Imp.builder()
                        .id("impId2")
                        .ext(mapper.valueToTree(ExtImp.of(
                                ExtImpPrebid.builder()
                                        .storedBidResponse(singletonList(
                                                ExtStoredBidResponse.of("rubicon", "storedBidRequest")))
                                        .build(),
                                null)))
                        .build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedAuctionRequest", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1").build())).build())));
        storedResponse.put("storedBidRequest", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id2").build())).build())));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

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
                                .bid(singletonList(Bid.builder().id("id2").impid("impId2").build()))
                                .build())));
    }

    @Test
    public void getStoredResponseResultShouldRemoveMockedBiddersFromImps() throws JsonProcessingException {
        final ObjectNode impExt = mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.builder()
                        .storedBidResponse(singletonList(ExtStoredBidResponse.of("rubicon", "storedBidResponseId1")))
                        .bidder(mapper.createObjectNode()
                                .put("rubicon", 1)
                                .put("appnexus", 2))
                        .build(),
                null));

        given(bidderCatalog.isValidName(any())).willReturn(true);

        final List<Imp> imps = singletonList(Imp.builder()
                .id("impId1")
                .ext(impExt).build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedBidResponseId1", mapper.writeValueAsString(singletonList(
                SeatBid.builder()
                        .seat("rubicon")
                        .bid(singletonList(Bid.builder().id("id1").build()))
                        .build())));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        final ObjectNode impExtResult = mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.builder()
                        .storedBidResponse(singletonList(ExtStoredBidResponse.of("rubicon", "storedBidResponseId1")))
                        .bidder(mapper.createObjectNode()
                                .put("appnexus", 2))
                        .build(),
                null));

        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(Imp.builder().id("impId1").ext(impExtResult).build()),
                singletonList(SeatBid.builder()
                        .seat("rubicon")
                        .bid(singletonList(Bid.builder().impid("impId1").id("id1").build()))
                        .build())));
    }

    @Test
    public void getStoredResponseResultShouldMergeStoredSeatBidsForTheSameBidder() throws JsonProcessingException {
        // given
        final List<Imp> imps = asList(
                Imp.builder()
                        .id("impId1")
                        .ext(mapper.valueToTree(ExtImp.of(
                                ExtImpPrebid.builder()
                                        .storedAuctionResponse(ExtStoredAuctionResponse.of("storedAuctionRequest"))
                                        .build(),
                                null)))
                        .build(),
                Imp.builder()
                        .id("impId2")
                        .ext(mapper.valueToTree(ExtImp.of(
                                ExtImpPrebid.builder()
                                        .storedBidResponse(singletonList(
                                                ExtStoredBidResponse.of("rubicon", "storedBidRequest")))
                                        .build(),
                                null)))
                        .build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedAuctionRequest", mapper.writeValueAsString(asList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1").build())).build(),
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id3").build())).build())));
        storedResponse.put("storedBidRequest", mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id2").build())).build())));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

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
                                        Bid.builder().id("id3").impid("impId1").build(),
                                        Bid.builder().id("id2").impid("impId2").build()))
                                .build())));
    }

    @Test
    public void getStoredResponseResultShouldSupportAliasesWhenDecidingIfImpRequiredRequestToExchange()
            throws JsonProcessingException {

        final ObjectNode impExt = mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.builder()
                        .storedBidResponse(singletonList(ExtStoredBidResponse.of("rubicon", "storedBidResponseId1")))
                        .bidder(mapper.createObjectNode()
                                .put("rubicon", 1)
                                .put("appnexusAlias", 2))
                        .build(),
                null));

        given(bidderCatalog.isValidName(any())).willReturn(false);

        final List<Imp> imps = singletonList(Imp.builder().id("impId1").ext(impExt).build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedBidResponseId1", mapper.writeValueAsString(singletonList(
                SeatBid.builder()
                        .seat("rubicon")
                        .bid(singletonList(Bid.builder().id("id1").build()))
                        .build())));

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, emptyList())));

        given(aliases.isAliasDefined(eq("appnexusAlias"))).willReturn(true);
        given(aliases.resolveBidder(eq("appnexusAlias"))).willReturn("appnexus");

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        final ObjectNode impExtResult = mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.builder()
                        .storedBidResponse(singletonList(ExtStoredBidResponse.of("rubicon", "storedBidResponseId1")))
                        .bidder(mapper.createObjectNode()
                                .put("appnexusAlias", 2))
                        .build(),
                null));

        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(Imp.builder().ext(impExtResult).id("impId1").build()),
                singletonList(SeatBid.builder()
                        .seat("rubicon")
                        .bid(singletonList(Bid.builder().id("id1").impid("impId1").build()))
                        .build())));
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenImpExtIsNotValid() {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .id("impId")
                .ext(mapper.createObjectNode().put("prebid", 5))
                .build());

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessageStartingWith("Error decoding bidRequest.imp.ext for impId = impId :");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenBidderIsMissedInStoredBidResponse() {
        // given
        final ObjectNode impExt = mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.builder()
                        .storedBidResponse(singletonList(ExtStoredBidResponse.of(null, "storedBidResponseId1")))
                        .build(),
                null));
        final List<Imp> imps = singletonList(Imp.builder().id("impId").ext(impExt).build());

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Bidder was not defined for imp.ext.prebid.storedBidResponse for imp with id impId");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenIdIsMissedInStoredBidResponse() {
        // given
        final ObjectNode impExt = mapper.valueToTree(ExtImp.of(
                ExtImpPrebid.builder()
                        .storedBidResponse(singletonList(ExtStoredBidResponse.of("rubicon", null)))
                        .build(),
                null));
        final List<Imp> imps = singletonList(Imp.builder().ext(impExt).id("impId").build());

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Id was not defined for imp.ext.prebid.storedBidResponse for imp with id impId");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenSeatIsEmptyInStoredSeatBid()
            throws JsonProcessingException {

        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().storedAuctionResponse(ExtStoredAuctionResponse.of("1")).build(),
                        null)))
                .build());

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(
                        singletonMap(
                                "responseId",
                                mapper.writeValueAsString(singletonList(SeatBid.builder()
                                        .bid(singletonList(Bid.builder().id("id").build()))
                                        .build()))),
                        emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Seat can't be empty in stored response seatBid");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenBidsAreEmptyInStoredSeatBid()
            throws JsonProcessingException {

        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().storedAuctionResponse(ExtStoredAuctionResponse.of("1")).build(),
                        null)))
                .build());

        given(applicationSettings.getStoredResponses(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(
                        singletonMap(
                                "responseId",
                                mapper.writeValueAsString(singletonList(SeatBid.builder()
                                        .seat("seat")
                                        .build()))),
                        emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("There must be at least one bid in stored response seatBid");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureSeatBidsCannotBeParsed() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().id("impId")
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder().storedAuctionResponse(ExtStoredAuctionResponse.of("1")).build(),
                        null)))
                .build());

        given(applicationSettings.getStoredResponses(any(), any())).willReturn(Future.succeededFuture(
                StoredResponseDataResult.of(singletonMap("1", "{invalid"), emptyList())));

        // when
        final Future<StoredResponseResult> result =
                storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Can't parse Json for stored response with id 1");
    }

    @Test
    public void mergeWithBidderResponsesShouldReturnMergedStoredSeatWithResponse() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")),
                        emptyList(),
                        emptyList()),
                100));

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<BidderResponse> result =
                storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid, imps);

        // then
        assertThat(result).contains(BidderResponse.of(
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
                                        "USD")),
                        emptyList(),
                        emptyList()),
                100));
    }

    @Test
    public void mergeWithBidderResponsesShouldMergeBidderResponsesWithoutCorrespondingStoredSeatBid() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")),
                        emptyList(),
                        emptyList()),
                100));

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("appnexus")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<BidderResponse> result =
                storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid, imps);

        // then
        assertThat(result).contains(
                BidderResponse.of(
                        "rubicon",
                        BidderSeatBid.of(
                                singletonList(BidderBid.of(
                                        Bid.builder().id("bid1").build(),
                                        BidType.banner,
                                        "USD")),
                                emptyList(),
                                emptyList()),
                        100),
                BidderResponse.of(
                        "appnexus",
                        BidderSeatBid.of(
                                singletonList(BidderBid.of(
                                        Bid.builder().id("bid2").impid("storedImp").build(),
                                        BidType.banner,
                                        "USD")),
                                emptyList(),
                                emptyList()),
                        0));
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
        final List<BidderResponse> result =
                storedResponseProcessor.mergeWithBidderResponses(emptyList(), seatBid, imps);

        // then
        assertThat(result).contains(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(
                                Bid.builder().id("bid2").impid("storedImp").build(),
                                BidType.banner,
                                "USD")),
                        emptyList(),
                        emptyList()),
                0));
    }

    @Test
    public void mergeWithBidderResponsesShouldResolveCurrencyFromBidderResponse() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "EUR")),
                        emptyList(),
                        emptyList()),
                100));

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon")
                .bid(singletonList(Bid.builder().id("bid2").impid("storedImp").build()))
                .build());

        final List<Imp> imps = singletonList(Imp.builder().id("storedImp").banner(Banner.builder().build()).build());

        // when
        final List<BidderResponse> result =
                storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid, imps);

        // then
        assertThat(result).contains(BidderResponse.of(
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
                                        "EUR")),
                        emptyList(),
                        emptyList()),
                100));
    }

    @Test
    public void mergeWithBidderResponsesShouldResolveBidTypeFromStoredBidExt() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")),
                        emptyList(),
                        emptyList()),
                100));

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
        final List<BidderResponse> result =
                storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid, imps);

        // then
        assertThat(result).contains(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        asList(BidderBid.of(
                                Bid.builder()
                                        .id("bid2")
                                        .impid("storedImp")
                                        .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(extBidPrebid)))
                                        .build(),
                                BidType.video,
                                "USD"),
                                BidderBid.of(
                                        Bid.builder()
                                                .id("bid1")
                                                .build(),
                                        BidType.banner,
                                        "USD")),
                        emptyList(),
                        emptyList()),
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
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")),
                        emptyList(),
                        emptyList()),
                100));

        final List<Imp> imps = singletonList(Imp.builder().banner(Banner.builder().build()).build());

        // when
        final List<BidderResponse> result =
                storedResponseProcessor.mergeWithBidderResponses(bidderResponses, emptyList(), imps);

        // then
        assertThat(result).containsOnly(BidderResponse.of(
                "rubicon",
                BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")),
                        emptyList(),
                        emptyList()),
                100));
    }
}
