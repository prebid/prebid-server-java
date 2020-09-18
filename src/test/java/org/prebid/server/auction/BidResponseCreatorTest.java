package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtOptions;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.VideoStoredDataResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source.xStatic;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class BidResponseCreatorTest extends VertxTest {

    private static final BidRequestCacheInfo CACHE_INFO = BidRequestCacheInfo.builder().build();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheService cacheService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private EventsService eventsService;
    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    private Clock clock;

    private Timeout timeout;

    private BidResponseCreator bidResponseCreator;

    @Before
    public void setUp() {
        given(cacheService.getEndpointHost()).willReturn("testHost");
        given(cacheService.getEndpointPath()).willReturn("testPath");
        given(cacheService.getCachedAssetURLTemplate()).willReturn("uuid=");

        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any()))
                .willReturn(Future.succeededFuture(VideoStoredDataResult.empty()));

        clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

        bidResponseCreator = new BidResponseCreator(
                cacheService,
                bidderCatalog,
                eventsService,
                storedRequestProcessor,
                false,
                0,
                clock,
                jacksonMapper);

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);
    }

    @Test
    public void shouldPassOriginalTimeoutToCacheServiceIfCachingIsRequested() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of(null, null)));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), same(timeout));
    }

    @Test
    public void shouldRequestCacheServiceWithExpectedArguments() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        final Bid bid3 = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(3.74)).build();
        final Bid bid4 = Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(6.74)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD"),
                        BidderBid.of(bid2, banner, "USD")), 100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid3, banner, "USD"),
                        BidderBid.of(bid4, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .cacheBidsTtl(99)
                .cacheVideoBidsTtl(101)
                .build();

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonMap(bid1, CacheIdInfo.of(null, null)));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false);

        // then
        Map<String, List<String>> biddersToCacheBidIds = new HashMap<>();
        biddersToCacheBidIds.put("bidder1", Arrays.asList("bidId1", "bidId2"));
        biddersToCacheBidIds.put("bidder2", Arrays.asList("bidId3", "bidId4"));
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid4, bid3, bid2))),
                eq(emptyList()),
                eq(CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .cacheBidsTtl(99)
                        .cacheVideoBidsTtl(101)
                        .bidderToVideoBidIdsToModify(emptyMap())
                        .bidderToBidIds(biddersToCacheBidIds)
                        .build()),
                eq(Account.builder().id("accountId").build()),
                eq(EventsContext.builder().auctionTimestamp(1000L).build()),
                eq(timeout));
    }

    @Test
    public void shouldRequestCacheServiceWithWinningBidsOnlyWhenWinningonlyIsTrue() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        final Bid bid3 = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(3.74)).build();
        final Bid bid4 = Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(6.74)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD"),
                        BidderBid.of(bid2, banner, "USD")), 100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid3, banner, "USD"),
                        BidderBid.of(bid4, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true).shouldCacheWinningBidsOnly(true).build();

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonMap(bid1, CacheIdInfo.of(null, null)));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false);

        // then
        Map<String, List<String>> biddersToCacheBidIds = new HashMap<>();
        biddersToCacheBidIds.put("bidder1", Arrays.asList("bidId1", "bidId2"));
        biddersToCacheBidIds.put("bidder2", Arrays.asList("bidId3", "bidId4"));
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid2)) && t.size() == 2),
                eq(emptyList()),
                eq(CacheContext.builder()
                        .bidderToVideoBidIdsToModify(emptyMap())
                        .bidderToBidIds(biddersToCacheBidIds)
                        .build()),
                eq(Account.builder().id("accountId").build()),
                eq(EventsContext.builder().auctionTimestamp(1000L).build()),
                eq(timeout));
    }

    @Test
    public void shouldRequestCacheServiceWithVideoBidsToModifyWhenEventsEnabledAndForBidderThatAllowsModifyVastXml() {
        // given
        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();

        final Imp imp1 = Imp.builder().id("impId1").video(Video.builder().build()).build();
        final Imp imp2 = Imp.builder().id("impId2").video(Video.builder().build()).build();

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.events(mapper.createObjectNode()),
                        imp1, imp2),
                contextBuilder -> contextBuilder.account(account));

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")), 100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid2, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheVideoBids(true)
                .build();

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonMap(bid1, CacheIdInfo.of(null, null)));

        given(bidderCatalog.isModifyingVastXmlAllowed(eq("bidder1"))).willReturn(true);

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false);

        // then
        Map<String, List<String>> biddersToCacheBidIds = new HashMap<>();
        biddersToCacheBidIds.put("bidder1", Collections.singletonList("bidId1"));
        biddersToCacheBidIds.put("bidder2", Collections.singletonList("bidId2"));
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid2))),
                eq(asList(imp1, imp2)),
                eq(CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder1", singletonList("bidId1")))
                        .bidderToBidIds(biddersToCacheBidIds)
                        .build()),
                same(account),
                eq(EventsContext.builder().auctionTimestamp(1000L).build()),
                eq(timeout));
    }

    @Test
    public void shouldCallCacheServiceEvenRoundedCpmIsZero() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(0.05)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        // just a stub to get through method call chain
        givenCacheServiceResult(singletonMap(bid1, CacheIdInfo.of(null, null)));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(
                argThat(bids -> bids.contains(bid1)),
                eq(emptyList()),
                eq(CacheContext.builder()
                        .bidderToVideoBidIdsToModify(emptyMap())
                        .bidderToBidIds(singletonMap("bidder1", Collections.singletonList("bidId1")))
                        .build()),
                eq(Account.builder().id("accountId").build()),
                eq(EventsContext.builder().auctionTimestamp(1000L).build()),
                eq(timeout));
    }

    @Test
    public void shouldSetExpectedConstantResponseFields() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, null, false).result();

        // then
        final BidResponse responseWithExpectedFields = BidResponse.builder()
                .id("123")
                .cur("USD")
                .ext(mapper.valueToTree(
                        ExtBidResponse.of(null, null, singletonMap("bidder1", 100), 1000L, null,
                                ExtBidResponsePrebid.of(1000L))))
                .build();

        assertThat(bidResponse)
                .isEqualToIgnoringGivenFields(responseWithExpectedFields, "nbr", "seatbid");

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());

    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesAreEmpty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        // when
        final BidResponse bidResponse = bidResponseCreator.create(emptyList(), auctionContext, null, false).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesDoNotContainAnyBids() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, null, false).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrNullAndPopulateSeatbidWhenAtLeastOneBidIsPresent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid bid = Bid.builder().impid("imp1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, null, null)), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getNbr()).isNull();
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSkipBidderResponsesWhereSeatBidContainEmptyBids() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid bid = Bid.builder().impid("imp1").build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(), 0),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldOverrideBidIdWhenGenerateBidIdIsTurnedOn() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());
        final ExtPrebid<ExtBidPrebid, ?> prebid = ExtPrebid.of(ExtBidPrebid.builder().type(banner).build(), null);
        final Bid bid = Bid.builder()
                .id("123")
                .impid("imp123")
                .ext(mapper.valueToTree(prebid))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        final BidResponseCreator bidResponseCreator = new BidResponseCreator(
                cacheService,
                bidderCatalog,
                eventsService,
                storedRequestProcessor,
                true,
                0,
                clock,
                jacksonMapper);

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("prebid"))
                .extracting(extPrebid -> mapper.treeToValue(extPrebid, ExtBidPrebid.class))
                .extracting(ExtBidPrebid::getBidid)
                .hasSize(1)
                .first()
                .satisfies(bidId -> assertThat(UUID.fromString(bidId)).isInstanceOf(UUID.class));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetExpectedResponseSeatBidAndBidFields() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());
        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).adm("adm").impid("i1")
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).containsOnly(SeatBid.builder()
                .seat("bidder1")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .impid("i1")
                        .price(BigDecimal.ONE)
                        .adm("adm")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                ExtBidPrebid.builder().type(banner).build(),
                                singletonMap("bidExt", 1))))
                        .build()))
                .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldFilterByDealsAndPriceBidsWhenImpIdsAreEqual() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid simpleBidImp1 = Bid.builder().id("bidId1i1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final Bid simpleBid1Imp2 = Bid.builder().id("bidId1i2").price(BigDecimal.valueOf(15.67)).impid("i2").build();
        final Bid simpleBid2Imp2 = Bid.builder().id("bidId2i2").price(BigDecimal.valueOf(17.67)).impid("i2").build();
        final Bid dealBid1Imp1 = Bid.builder().id("bidId1i1d").dealid("d1").price(BigDecimal.valueOf(4.98)).impid("i1")
                .build();
        final Bid dealBid2Imp1 = Bid.builder().id("bidId2i1d").dealid("d2").price(BigDecimal.valueOf(5.00)).impid("i1")
                .build();
        final BidderSeatBid seatBidWithDeals = givenSeatBid(
                BidderBid.of(simpleBidImp1, banner, null),
                BidderBid.of(simpleBid1Imp2, banner, null),
                BidderBid.of(simpleBid2Imp2, banner, null), // will stay (top price)
                BidderBid.of(dealBid2Imp1, banner, null),   // will stay (deal + topPrice)
                BidderBid.of(dealBid1Imp1, banner, null));

        final Bid simpleBid3Imp2 = Bid.builder().id("bidId3i2").price(BigDecimal.valueOf(7.25)).impid("i2").build();
        final Bid simpleBid4Imp2 = Bid.builder().id("bidId4i2").price(BigDecimal.valueOf(7.26)).impid("i2").build();
        final BidderSeatBid seatBidWithSimpleBids = givenSeatBid(
                BidderBid.of(simpleBid3Imp2, banner, null),
                BidderBid.of(simpleBid4Imp2, banner, null)); // will stay (top price)

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", seatBidWithDeals, 100),
                BidderResponse.of("bidder2", seatBidWithSimpleBids, 111));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(Bid::getId)
                .containsOnly("bidId2i2", "bidId2i1d", "bidId4i2");
    }

    @Test
    public void shouldAddTypeToNativeBidAdm() throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(singletonList(Asset.builder()
                        .id(123)
                        .img(ImageObject.builder().type(1).build())
                        .data(DataObject.builder().type(2).build())
                        .build()))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .tmax(1000L)
                .app(App.builder().build())
                .imp(singletonList(Imp.builder()
                        .id("imp1")
                        .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                        .build()))
                .build();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Response responseAdm = Response.builder()
                .assets(singletonList(com.iab.openrtb.response.Asset.builder()
                        .id(123)
                        .img(com.iab.openrtb.response.ImageObject.builder().build())
                        .data(com.iab.openrtb.response.DataObject.builder().build())
                        .build()))
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid("imp1")
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(adm -> mapper.readValue(adm, Response.class))
                .flatExtracting(Response::getAssets).hasSize(1)
                .containsOnly(com.iab.openrtb.response.Asset.builder()
                        .id(123)
                        .img(com.iab.openrtb.response.ImageObject.builder().type(1).build())
                        .data(com.iab.openrtb.response.DataObject.builder().type(2).build())
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldReturnEmptyAssetIfImageTypeIsEmpty() throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(singletonList(Asset.builder()
                        .id(123)
                        .img(ImageObject.builder().type(null).build())
                        .data(DataObject.builder().type(2).build())
                        .build()))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .tmax(1000L)
                .app(App.builder().build())
                .imp(singletonList(Imp.builder()
                        .id("imp1")
                        .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                        .build()))
                .build();

        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Response responseAdm = Response.builder()
                .assets(singletonList(com.iab.openrtb.response.Asset.builder()
                        .id(123)
                        .img(com.iab.openrtb.response.ImageObject.builder().type(null).build())
                        .data(com.iab.openrtb.response.DataObject.builder().build())
                        .build()))
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid("imp1")
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(adm -> mapper.readValue(adm, Response.class))
                .flatExtracting(Response::getAssets)
                .isEmpty();
    }

    @Test
    public void shouldReturnEmptyAssetIfDataTypeIsEmpty() throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(singletonList(Asset.builder()
                        .id(123)
                        .img(ImageObject.builder().type(1).build())
                        .data(DataObject.builder().type(null).build())
                        .build()))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .tmax(1000L)
                .app(App.builder().build())
                .imp(singletonList(Imp.builder()
                        .id("imp1")
                        .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                        .build()))
                .build();

        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Response responseAdm = Response.builder()
                .assets(singletonList(com.iab.openrtb.response.Asset.builder()
                        .id(123)
                        .img(com.iab.openrtb.response.ImageObject.builder().build())
                        .data(com.iab.openrtb.response.DataObject.builder().build())
                        .build()))
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid("imp1")
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(adm -> mapper.readValue(adm, Response.class))
                .flatExtracting(Response::getAssets)
                .isEmpty();
    }

    @Test
    public void shouldSetBidAdmToNullIfCacheIdIsPresentAndReturnCreativeBidsIsFalse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().price(BigDecimal.ONE).adm("adm").impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("id", null)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetBidAdmToNullIfVideoCacheIdIsPresentAndReturnCreativeVideoBidsIsFalse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().price(BigDecimal.ONE).adm("adm").impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("id", null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());
        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .containsOnly(Bid.builder()
                        .id("bidId")
                        .impid("i1")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.builder().type(banner).build(), null)))
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_bidder_bidder1", "bidder1"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldTruncateTargetingKeywordsByGlobalConfig() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidResponseCreator bidResponseCreator = new BidResponseCreator(
                cacheService,
                bidderCatalog,
                eventsService,
                storedRequestProcessor,
                false,
                20,
                clock,
                jacksonMapper);

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_someVeryLongBi", "5.00"),
                        tuple("hb_bidder", "someVeryLongBidderName"),
                        tuple("hb_bidder_someVeryLo", "someVeryLongBidderName"));
    }

    @Test
    public void shouldTruncateTargetingKeywordsByAccountConfig() {
        // given
        final Account account = Account.builder().id("accountId").truncateTargetAttr(20).build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()));
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_someVeryLongBi", "5.00"),
                        tuple("hb_bidder", "someVeryLongBidderName"),
                        tuple("hb_bidder_someVeryLo", "someVeryLongBidderName"));
    }

    @Test
    public void shouldTruncateTargetingKeywordsByRequestPassedValue() {
        // given
        final Account account = Account.builder().id("accountId").truncateTargetAttr(25).build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))))
                        .includewinners(true)
                        .includebidderkeys(true)
                        .truncateattrchars(20)
                        .build()));
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_someVeryLongBi", "5.00"),
                        tuple("hb_bidder", "someVeryLongBidderName"),
                        tuple("hb_bidder_someVeryLo", "someVeryLongBidderName"));
    }

    @Test
    public void shouldPopulateTargetingKeywordsForWinningBidsAndWinningBidsByBidder() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(4.98)).impid("i2").build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(7.25)).impid("i2").build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1",
                        givenSeatBid(BidderBid.of(firstBid, banner, null),
                                BidderBid.of(secondBid, banner, null)), 100),
                BidderResponse.of("bidder2",
                        givenSeatBid(BidderBid.of(thirdBid, banner, null)), 111));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder2"))
                .containsOnly(
                        tuple("bidId1", "bidder1", "bidder1", null),
                        tuple("bidId2", null, "bidder1", null),
                        tuple("bidId3", "bidder2", null, "bidder2"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsFromMediaTypePriceGranularities() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))))
                        .mediatypepricegranularity(ExtMediaTypePriceGranularity.of(
                                mapper.valueToTree(ExtPriceGranularity.of(
                                        3,
                                        singletonList(ExtGranularityRange.of(
                                                BigDecimal.valueOf(10), BigDecimal.valueOf(1))))),
                                null,
                                null))
                        .includewinners(true)
                        .includebidderkeys(true)
                        .build())));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.000"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_pb_bidder1", "5.000"),
                        tuple("hb_bidder_bidder1", "bidder1"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateCacheIdHostPathAndUuidTargetingKeywords() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_cache_id", "cacheId"),
                        tuple("hb_uuid", "videoId"),
                        tuple("hb_cache_host", "testHost"),
                        tuple("hb_cache_path", "testPath"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder_bidder1", "bidder1"),
                        tuple("hb_cache_id_bidder1", "cacheId"),
                        tuple("hb_uuid_bidder1", "videoId"),
                        tuple("hb_cache_host_bidder1", "testHost"),
                        tuple("hb_cache_path_bidder1", "testPath"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsWithEventsUrl() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .eventsEnabled(true)
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .targeting(givenTargeting())
                        .events(mapper.createObjectNode()));
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid("i1")
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong(), any()))
                .willReturn("http://win-url");

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        verify(eventsService).winUrlTargeting(eq("bidder1"), eq("accountId"), eq(1000L), isNull());
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_bidder_bidder1", "bidder1"),
                        tuple("hb_winurl", "http%3A%2F%2Fwin-url"),
                        tuple("hb_bidid", "bidId1"),
                        tuple("hb_bidid_bidder1", "bidId1"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsWithAdditionalValuesFromRequest() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .targeting(givenTargeting())
                        .adservertargeting(singletonList(ExtRequestPrebidAdservertargetingRule.of(
                                "static_keyword1", xStatic, "static_keyword1")))));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("static_keyword1", "static_keyword1"));
    }

    @Test
    public void shouldAddExtPrebidEvents() {
        // given
        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .events(mapper.createObjectNode())
                        .integration("pbjs"));
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid("i1")
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), anyLong(), anyString()))
                .willReturn(events);
        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong(), anyString()))
                .willReturn("http://win-url");

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsOnly(events);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldAddExtPrebidVideo() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest());

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").impid("i1")
                .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(
                        ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(1, "category")).build()))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong(), anyString()))
                .willReturn("http://win-url");

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getVideo())
                .containsOnly(ExtBidPrebidVideo.of(1, "category"));
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfEventsAreNotEnabled() {
        // given
        final Account account = Account.builder().id("accountId").eventsEnabled(false).build();
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.events(mapper.createObjectNode())),
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsNull();
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfExtRequestPrebidEventsNull() {
        // given
        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(),
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid("i1")
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong(), any())).willReturn("http://win-url");

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsNull();
    }

    @Test
    public void shouldReturnCacheEntityInExt() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getCache())
                .extracting(ExtResponseCache::getBids, ExtResponseCache::getVastXml)
                .containsExactly(tuple(
                        CacheAsset.of("uuid=cacheId", "cacheId"),
                        CacheAsset.of("uuid=videoId", "videoId")));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateWinningBidTargetingIfIncludeWinnersFlagIsFalse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))))
                        .includewinners(false)
                        .includebidderkeys(true)
                        .build())));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder"),
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", null, "bidder1"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateBidderKeysTargetingIfIncludeBidderKeysFlagIsFalse() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))))
                        .includewinners(true)
                        .includebidderkeys(false)
                        .build())));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder"),
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", "bidder1", null));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateCacheIdTargetingKeywordsIfBidCpmIsZero() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid firstBid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid secondBid = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(firstBid, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(secondBid, banner, null)), 123));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(secondBid, CacheIdInfo.of("cacheId2", null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).hasSize(2)
                .extracting(
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_cache_id"),
                        bid -> toTargetingByKey(bid, "hb_cache_id_bidder2"))
                .containsOnly(
                        tuple("bidder1", null, null),
                        tuple("bidder2", "cacheId2", "cacheId2"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .tmax(1000L)
                .app(App.builder().build())
                .imp(emptyList())
                .build();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").adm("[]").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, xNative, null)), null,
                        singletonList(BidderError.badInput("bad_input"))), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(CacheServiceResult.of(
                DebugHttpCall.builder().endpoint("http://cache-service/cache").responseTimeMillis(666).build(),
                new RuntimeException("cacheError"), emptyMap()));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false).result();

        // then
        final ExtBidResponse responseExt = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);

        assertThat(responseExt.getDebug()).isNull();
        assertThat(responseExt.getUsersync()).isNull();
        assertThat(responseExt.getTmaxrequest()).isEqualTo(1000L);

        assertThat(responseExt.getErrors()).hasSize(2).containsOnly(
                entry("bidder1", asList(
                        ExtBidderError.of(2, "bad_input"),
                        ExtBidderError.of(3, "Failed to decode: Cannot deserialize instance of `com.iab."
                                + "openrtb.response.Response` out of START_ARRAY token\n at [Source: (String)\"[]\"; "
                                + "line: 1, column: 1]"))),
                entry("prebid", singletonList(ExtBidderError.of(999, "cacheError"))));

        assertThat(responseExt.getResponsetimemillis()).hasSize(2)
                .containsOnly(entry("bidder1", 100), entry("cache", 666));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void impToStoredVideoJsonShouldTolerateWhenStoredVideoFetchIsFailed() {
        // given
        final Imp imp = Imp.builder().id("impId1").ext(
                mapper.valueToTree(
                        ExtImp.of(
                                ExtImpPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of("st1"))
                                        .options(ExtOptions.of(true))
                                        .build(),
                                null
                        )))
                .build();
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(imp));

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any())).willReturn(
                Future.failedFuture("Fetch failed"));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(eq(singletonList(imp)), any(), eq(timeout));

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void impToStoredVideoJsonShouldInjectStoredVideoWhenExtOptionsIsTrueAndVideoNotEmpty() {
        // given
        final Imp imp1 = Imp.builder().id("impId1").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("st1"))
                                .options(ExtOptions.of(true))
                                .build(), null)))
                .build();
        final Imp imp2 = Imp.builder().id("impId2").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("st2"))
                                .options(ExtOptions.of(false))
                                .build(), null)))
                .build();
        final Imp imp3 = Imp.builder().id("impId3").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder()
                                .storedrequest(ExtStoredRequest.of("st3"))
                                .options(ExtOptions.of(true))
                                .build(), null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(imp1, imp2, imp3);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(2)).build();
        final Bid bid3 = Bid.builder().id("bidId3").impid("impId3").price(BigDecimal.valueOf(3)).build();
        final List<BidderBid> bidderBids = Arrays.asList(
                BidderBid.of(bid1, banner, "USD"),
                BidderBid.of(bid2, banner, "USD"),
                BidderBid.of(bid3, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        final Video storedVideo = Video.builder().maxduration(100).h(2).w(2).build();
        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        VideoStoredDataResult.of(singletonMap("impId1", storedVideo), emptyList())));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(eq(Arrays.asList(imp1, imp3)), any(), eq(timeout));

        assertThat(result.result().getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getStoredRequestAttributes())
                .containsOnly(storedVideo, null, null);
    }

    @Test
    public void impToStoredVideoJsonShouldAddErrorsWithPrebidBidderWhenStoredVideoRequestFailed() {
        // given
        final Imp imp1 = Imp.builder().id("impId1").ext(
                mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of("st1"))
                                        .options(ExtOptions.of(true))
                                        .build(),
                                null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(imp1);
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(
                BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any()))
                .willReturn(Future.failedFuture("Bad timeout"));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(eq(singletonList(imp1)), any(), eq(timeout));

        assertThat(result.result().getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        "prebid", singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                                "Bad timeout"))), singletonMap("bidder1", 100), 1000L, null,
                        ExtBidResponsePrebid.of(1000L))));
    }

    @Test
    public void shouldProcessRequestAndAddErrorAboutDeprecatedBidder() {
        // given
        final String invalidBidderName = "invalid";

        final BidRequest bidRequest = givenBidRequest(Imp.builder()
                .ext(mapper.valueToTree(singletonMap(invalidBidderName, 0)))
                .build());
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        given(bidderCatalog.isDeprecatedName(invalidBidderName)).willReturn(true);
        given(bidderCatalog.errorForDeprecatedName(invalidBidderName)).willReturn(
                "invalid has been deprecated and is no longer available. Use valid instead.");

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false).result();

        // then
        assertThat(bidResponse.getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        invalidBidderName, singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                "invalid has been deprecated and is no longer available. Use valid instead."))),
                        singletonMap("bidder1", 100), 1000L, null, ExtBidResponsePrebid.of(1000L))));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldProcessRequestAndAddErrorFromAuctionContext() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(),
                contextBuilder -> contextBuilder.prebidErrors(singletonList("privacy error")));

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(
                BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, false);

        // then
        assertThat(result.result().getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        "prebid", singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                                "privacy error"))), singletonMap("bidder1", 100), 1000L, null,
                        ExtBidResponsePrebid.of(1000L))));
    }

    @Test
    public void shouldPopulateBidResponseDebugExtensionIfDebugIsEnabled() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);
        givenCacheServiceResult(CacheServiceResult.of(
                DebugHttpCall.builder().endpoint("http://cache-service/cache")
                        .requestUri("test.uri").responseStatus(500).build(), null, emptyMap()));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, banner, null)),
                        singletonList(ExtHttpCall.builder().status(200).build()), null), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, true).result();

        // then
        final ExtBidResponse responseExt = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);

        assertThat(responseExt.getDebug()).isNotNull();
        assertThat(responseExt.getDebug().getHttpcalls()).hasSize(2)
                .containsOnly(
                        entry("bidder1", singletonList(ExtHttpCall.builder().status(200).build())),
                        entry("cache", singletonList(ExtHttpCall.builder().uri("test.uri").status(500).build())));

        assertThat(responseExt.getDebug().getResolvedrequest()).isEqualTo(bidRequest);

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPassIntegrationToCacheServiceAndBidEvents() {
        // given
        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();
        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .imp(emptyList())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .events(mapper.createObjectNode())
                        .integration("integration")
                        .build()))
                .build();
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of(null, null)));

        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong(), anyString()))
                .willReturn("http://win-url");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), anyLong(), anyString()))
                .willReturn(Events.of(
                        "http://win-url?param=value&int=integration",
                        "http://imp-url?param=value&int=integration"));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(),
                argThat(eventsContext -> eventsContext.getIntegration().equals("integration")), any());

        assertThat(result.result().getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getEvents())
                .containsOnly(Events.of("http://win-url?param=value&int=integration",
                        "http://imp-url?param=value&int=integration"));
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest,
                                               UnaryOperator<AuctionContext.AuctionContextBuilder> contextCustomizer) {

        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .account(Account.empty("accountId"))
                .bidRequest(bidRequest)
                .timeout(timeout)
                .prebidErrors(emptyList());

        return contextCustomizer.apply(auctionContextBuilder)
                .build();
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return givenAuctionContext(bidRequest, identity());
    }

    private void givenCacheServiceResult(Map<Bid, CacheIdInfo> cacheBids) {
        givenCacheServiceResult(CacheServiceResult.of(null, null, cacheBids));
    }

    private void givenCacheServiceResult(CacheServiceResult cacheServiceResult) {
        given(cacheService.cacheBidsOpenrtb(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(cacheServiceResult));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<ExtRequestPrebid.ExtRequestPrebidBuilder> extRequestCustomizer,
            Imp... imps) {

        final ExtRequestPrebid.ExtRequestPrebidBuilder extRequestBuilder = ExtRequestPrebid.builder();

        final BidRequest.BidRequestBuilder bidRequestBuilder = BidRequest.builder()
                .id("123")
                .cur(singletonList("USD"))
                .tmax(1000L)
                .imp(asList(imps))
                .ext(ExtRequest.of(extRequestCustomizer.apply(extRequestBuilder).build()));

        return bidRequestCustomizer.apply(bidRequestBuilder)
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return givenBidRequest(bidRequestCustomizer, identity(), imps);
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static BidderSeatBid givenSeatBid(BidderBid... bids) {
        return BidderSeatBid.of(new ArrayList<>(asList(bids)), emptyList(), emptyList());
    }

    private static ExtRequestTargeting givenTargeting() {
        return ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(
                        ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                BigDecimal.valueOf(0.5))))))
                .includewinners(true)
                .includebidderkeys(true)
                .build();
    }

    private static ExtPrebid<ExtBidPrebid, ?> toExtPrebid(ObjectNode ext) {
        try {
            return mapper.readValue(mapper.treeAsTokens(ext), new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            });
        } catch (IOException e) {
            return rethrow(e);
        }
    }

    private static String toTargetingByKey(Bid bid, String targetingKey) {
        final Map<String, String> targeting = toExtPrebid(bid.getExt()).getPrebid().getTargeting();
        return targeting != null ? targeting.get(targetingKey) : null;
    }
}
