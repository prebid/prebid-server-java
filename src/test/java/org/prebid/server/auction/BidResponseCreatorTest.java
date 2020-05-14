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
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpCall;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class BidResponseCreatorTest extends VertxTest {

    private static final Account ACCOUNT = Account.builder().id("accountId").build(); // never be null
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

    private Timeout timeout;

    private BidResponseCreator bidResponseCreator;

    @Before
    public void setUp() {
        given(cacheService.getEndpointHost()).willReturn("testHost");
        given(cacheService.getEndpointPath()).willReturn("testPath");
        given(cacheService.getCachedAssetURLTemplate()).willReturn("uuid=");

        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any()))
                .willReturn(Future.succeededFuture(VideoStoredDataResult.empty()));

        bidResponseCreator = new BidResponseCreator(cacheService, bidderCatalog, eventsService, storedRequestProcessor,
                jacksonMapper);

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);
    }

    @Test
    public void shouldPassOriginalTimeoutToCacheServiceIfCachingIsRequested() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of(null, null)));

        // when
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT, false, 1000L, false, timeout);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), same(timeout));
    }

    @Test
    public void shouldRequestCacheServiceWithExpectedArguments() {
        // given
        final BidRequest bidRequest = givenBidRequest();

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
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT, false, 1000L, false, timeout);

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

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

        // when\
        bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo, ACCOUNT, false, 1000L, false,
                timeout);

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
        final Imp imp1 = Imp.builder().id("impId1").video(Video.builder().build()).build();
        final Imp imp2 = Imp.builder().id("impId2").video(Video.builder().build()).build();
        final BidRequest bidRequest = givenBidRequest(imp1, imp2);

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")), 100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid2, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheVideoBids(true)
                .build();
        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonMap(bid1, CacheIdInfo.of(null, null)));

        given(bidderCatalog.isModifyingVastXmlAllowed(eq("bidder1"))).willReturn(true);

        // when
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, account, true, 1000L, false, timeout);

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
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(0.05)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        // just a stub to get through method call chain
        givenCacheServiceResult(singletonMap(bid1, CacheIdInfo.of(null, null)));

        // when
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT, false, 1000L, false, timeout);

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
        final BidRequest bidRequest = givenBidRequest();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, null, ACCOUNT,
                false, 1000L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(emptyList(), bidRequest, null, null, ACCOUNT, false,
                0L, false, timeout).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesDoNotContainAnyBids() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, null, ACCOUNT,
                false, 1000L, false, timeout).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrNullAndPopulateSeatbidWhenAtLeastOneBidIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, null, null)), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 1000L, false, timeout).result();

        // then
        assertThat(bidResponse.getNbr()).isNull();
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSkipBidderResponsesWhereSeatBidContainEmptyBids() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(), 0),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(Bid.builder().build(), banner, "USD")), 0));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 1000L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldSetExpectedResponseSeatBidAndBidFields() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).adm("adm")
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 1000L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).containsOnly(SeatBid.builder()
                .seat("bidder1")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .adm("adm")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                ExtBidPrebid.of(banner, null, null, null, null, null), singletonMap("bidExt", 1))))
                        .build()))
                .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 1000L, false, timeout).result();

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
    public void shouldSetBidAdmToNullIfCacheIdIsPresentAndReturnCreativeBidsIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().price(BigDecimal.ONE).adm("adm").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("id", null)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().price(BigDecimal.ONE).adm("adm").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("id", null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 1000L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .containsOnly(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(ExtBidPrebid.of(banner, null, null, null, null, null), null)))
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                ACCOUNT, false, 1000L, false, timeout).result();

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
    public void shouldPopulateTargetingKeywordsForWinningBidsAndWinningBidsByBidder() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(4.98)).build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(7.25)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1",
                        givenSeatBid(BidderBid.of(firstBid, banner, null),
                                BidderBid.of(secondBid, banner, null)), 100),
                BidderResponse.of("bidder2",
                        givenSeatBid(BidderBid.of(thirdBid, banner, null)), 111));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                ACCOUNT, false, 0L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder2"))
                .containsOnly(
                        tuple("bidId1", null, "bidder1", null),
                        tuple("bidId2", null, null, null),
                        tuple("bidId3", "bidder2", null, "bidder2"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsFromMediaTypePriceGranularities() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtPriceGranularity priceGranularity = ExtPriceGranularity.of(2,
                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))));
        final ExtMediaTypePriceGranularity mediaTypePriceGranuality = ExtMediaTypePriceGranularity.of(
                mapper.valueToTree(
                        ExtPriceGranularity.of(3, singletonList(
                                ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(1))))), null, null);
        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(priceGranularity))
                .mediatypepricegranularity(mediaTypePriceGranuality)
                .includewinners(true)
                .includebidderkeys(true)
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();

        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong())).willReturn("http://win-url");

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                account, true, 0L, false, timeout).result();

        // then
        verify(eventsService).winUrlTargeting(eq("bidder1"), eq("accountId"), eq(0L));
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
    public void shouldAddExtPrebidEvents() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), anyLong())).willReturn(events);
        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong())).willReturn("http://win-url");

        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                account, true, 0L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsOnly(events);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfEventsAreNotEnabled() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Account account = Account.builder().id("accountId").eventsEnabled(false).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                account, true, 0L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsNull();
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfExtRequestPrebidEventsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(eventsService.winUrlTargeting(anyString(), anyString(), anyLong())).willReturn("http://win-url");
        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, CACHE_INFO,
                account, false, 0L, false, timeout).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsNull();
    }

    @Test
    public void shouldReturnCacheEntityInExt() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(
                        ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                BigDecimal.valueOf(0.5))))))
                .includewinners(false)
                .includebidderkeys(true)
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(
                        ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                BigDecimal.valueOf(0.5))))))
                .includewinners(true)
                .includebidderkeys(false)
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonMap(bid, CacheIdInfo.of("cacheId", "videoId")));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid firstBid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid secondBid = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(firstBid, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(secondBid, banner, null)), 123));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonMap(secondBid, CacheIdInfo.of("cacheId2", null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo,
                ACCOUNT, false, 0L, false, timeout).result();

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

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").adm("[]").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, xNative, null)), null,
                        singletonList(BidderError.badInput("bad_input"))), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(CacheServiceResult.of(
                CacheHttpCall.of(null, null, 666), new RuntimeException("cacheError"), emptyMap()));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT,
                false, 0L, false, timeout).result();

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
        final BidRequest bidRequest = givenBidRequest(imp);

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any())).willReturn(
                Future.failedFuture("Fetch failed"));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 0L, false, timeout);

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
        final Future<BidResponse> result = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 0L, false, timeout);

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

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(
                BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        given(storedRequestProcessor.videoStoredDataResult(any(), any(), any()))
                .willReturn(Future.failedFuture("Bad timeout"));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 0L, false, timeout);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(eq(singletonList(imp1)), any(), eq(timeout));

        assertThat(result.result().getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        "prebid", singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(),
                                "Bad timeout"))), singletonMap("bidder1", 100), 1000L, null,
                        ExtBidResponsePrebid.of(0L))));
    }

    @Test
    public void shouldProcessRequestAndAddErrorAboutDeprecatedBidder() {
        // given
        final String invalidBidderName = "invalid";

        final BidRequest bidRequest = givenBidRequest(Imp.builder()
                .ext(mapper.valueToTree(singletonMap(invalidBidderName, 0)))
                .build());

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        given(bidderCatalog.isDeprecatedName(invalidBidderName)).willReturn(true);
        given(bidderCatalog.errorForDeprecatedName(invalidBidderName)).willReturn(
                "invalid has been deprecated and is no longer available. Use valid instead.");

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, CACHE_INFO,
                ACCOUNT, false, 0L, false, timeout).result();

        // then
        assertThat(bidResponse.getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        invalidBidderName, singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                "invalid has been deprecated and is no longer available. Use valid instead."))),
                        singletonMap("bidder1", 100), 1000L, null, ExtBidResponsePrebid.of(0L))));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    public void shouldPopulateBidResponseDebugExtensionIfDebugIsEnabled() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest();
        givenCacheServiceResult(CacheServiceResult.of(
                CacheHttpCall.of(CacheHttpRequest.of("test.uri", null), CacheHttpResponse.of(500, null), null),
                null, emptyMap()));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, banner, null)),
                        singletonList(ExtHttpCall.builder().status(200).build()), null), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT,
                false, 0L, true, timeout).result();

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

    private void givenCacheServiceResult(Map<Bid, CacheIdInfo> cacheBids) {
        givenCacheServiceResult(CacheServiceResult.of(null, null, cacheBids));
    }

    private void givenCacheServiceResult(CacheServiceResult cacheServiceResult) {
        given(cacheService.cacheBidsOpenrtb(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(cacheServiceResult));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder()
                .id("123")
                .cur(singletonList("USD"))
                .tmax(1000L)
                .imp(asList(imps))
                .build();
    }

    private static BidderSeatBid givenSeatBid(BidderBid... bids) {
        return BidderSeatBid.of(asList(bids), emptyList(), emptyList());
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
