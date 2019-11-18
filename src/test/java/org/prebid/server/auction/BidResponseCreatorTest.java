package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
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
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.settings.model.Account;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

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

    private Timeout timeout;

    private BidResponseCreator bidResponseCreator;

    @Before
    public void setUp() {
        given(cacheService.getEndpointHost()).willReturn("testHost");
        given(cacheService.getEndpointPath()).willReturn("testPath");
        given(cacheService.getCachedAssetURLTemplate()).willReturn("uuid=");

        bidResponseCreator = new BidResponseCreator(cacheService, bidderCatalog, eventsService);

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
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT, timeout, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), same(timeout));
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
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT, timeout, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid4, bid3, bid2))), eq(emptyList()),
                eq(CacheContext.builder().shouldCacheBids(true).shouldCacheVideoBids(true).cacheBidsTtl(99)
                        .cacheVideoBidsTtl(101).videoBidIdsToModify(emptyList()).build()),
                eq(Account.builder().id("accountId").build()), eq(timeout));
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

        // when
        bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo, ACCOUNT, timeout, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid2)) && t.size() == 2), eq(emptyList()),
                eq(CacheContext.builder().videoBidIdsToModify(emptyList()).build()),
                eq(Account.builder().id("accountId").build()), eq(timeout));
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
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, account, timeout, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(
                argThat(t -> t.containsAll(asList(bid1, bid2))), eq(asList(imp1, imp2)),
                eq(CacheContext.builder().shouldCacheVideoBids(true).videoBidIdsToModify(singletonList("bidId1"))
                        .build()),
                same(account), eq(timeout));
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
        bidResponseCreator.create(bidderResponses, bidRequest, null, cacheInfo, ACCOUNT, timeout, false);

        // then
        verify(cacheService).cacheBidsOpenrtb(
                argThat(bids -> bids.contains(bid1)), eq(emptyList()),
                eq(CacheContext.builder().videoBidIdsToModify(emptyList()).build()),
                eq(Account.builder().id("accountId").build()), eq(timeout));
    }

    @Test
    public void shouldSetExpectedConstantResponseFields() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, null, ACCOUNT, timeout, false).result();

        // then
        final BidResponse responseWithExpectedFields = BidResponse.builder()
                .id("123")
                .cur("USD")
                .ext(mapper.valueToTree(
                        ExtBidResponse.of(null, null, singletonMap("bidder1", 100), 1000L, null)))
                .build();

        assertThat(bidResponse)
                .isEqualToIgnoringGivenFields(responseWithExpectedFields, "nbr", "seatbid");

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(emptyList(), bidRequest, null,
                null, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesDoNotContainAnyBids() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null,
                null, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrNullAndPopulateSeatbidWhenAtLeastOneBidIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, null, null)), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null,
                CACHE_INFO, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getNbr()).isNull();
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldSkipBidderResponsesWhereSeatBidContainEmptyBids() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(), 0),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(Bid.builder().build(), banner, "USD")), 0));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, CACHE_INFO, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, CACHE_INFO, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).containsOnly(SeatBid.builder()
                .seat("bidder1")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .adm("adm")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                ExtBidPrebid.of(banner, null, null, null), singletonMap("bidExt", 1))))
                        .build()))
                .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, CACHE_INFO, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .containsOnly(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(banner, null, null, null), null)))
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, CACHE_INFO, ACCOUNT, timeout, false).result();

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

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, CACHE_INFO, ACCOUNT, timeout, false).result();

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

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsFromMediaTypePriceGranularities() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), ExtMediaTypePriceGranularity.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(3, singletonList(
                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(1))))), null, null),
                null, true, true);

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, CACHE_INFO, ACCOUNT, timeout, false).result();

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

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

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

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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

        given(eventsService.winUrlTargeting(anyString())).willReturn("http://win-url");

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, CACHE_INFO, account, timeout, false).result();

        // then
        verify(eventsService).winUrlTargeting(eq("accountId"));
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

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        given(eventsService.createEvent(anyString(), anyString())).willReturn(events);
        given(eventsService.winUrlTargeting(anyString())).willReturn("http://win-url");

        final Account account = Account.builder().id("accountId").eventsEnabled(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, CACHE_INFO, account, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtPrebid(responseBid.getExt()).getPrebid().getEvents())
                .containsOnly(events);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getCache())
                .extracting(ExtResponseCache::getBids, ExtResponseCache::getVastXml)
                .containsExactly(tuple(
                        CacheAsset.of("uuid=cacheId", "cacheId"),
                        CacheAsset.of("uuid=videoId", "videoId")));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateWinningBidTargetingIfIncludeWinnersFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, false, true);

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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder"),
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", null, "bidder1"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateBidderKeysTargetingIfIncludeBidderKeysFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, true, false);

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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder"),
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", "bidder1", null));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).hasSize(2)
                .extracting(
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_cache_id"),
                        bid -> toTargetingByKey(bid, "hb_cache_id_bidder2"))
                .containsOnly(
                        tuple("bidder1", null, null),
                        tuple("bidder2", "cacheId2", "cacheId2"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest();

        final Bid bid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, banner, null)), null,
                        singletonList(BidderError.badInput("bad_input"))), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(CacheServiceResult.of(
                CacheHttpCall.of(null, null, 666), new RuntimeException("cacheError"), emptyMap()));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheInfo, ACCOUNT, timeout, false).result();

        // then
        final ExtBidResponse responseExt = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);

        assertThat(responseExt.getDebug()).isNull();
        assertThat(responseExt.getUsersync()).isNull();
        assertThat(responseExt.getTmaxrequest()).isEqualTo(1000L);

        assertThat(responseExt.getErrors()).hasSize(2)
                .containsOnly(
                        entry("bidder1", singletonList(ExtBidderError.of(2, "bad_input"))),
                        entry("prebid", singletonList(ExtBidderError.of(999, "cacheError"))));

        assertThat(responseExt.getResponsetimemillis()).hasSize(2)
                .containsOnly(entry("bidder1", 100), entry("cache", 666));

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, CACHE_INFO, ACCOUNT, timeout, false).result();

        // then
        assertThat(bidResponse.getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        invalidBidderName, singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                "invalid has been deprecated and is no longer available. Use valid instead."))),
                        singletonMap("bidder1", 100), 1000L, null)));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
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
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheInfo, ACCOUNT, timeout, true).result();

        // then
        final ExtBidResponse responseExt = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);

        assertThat(responseExt.getDebug()).isNotNull();
        assertThat(responseExt.getDebug().getHttpcalls()).hasSize(2)
                .containsOnly(
                        entry("bidder1", singletonList(ExtHttpCall.builder().status(200).build())),
                        entry("cache", singletonList(ExtHttpCall.builder().uri("test.uri").status(500).build())));

        assertThat(responseExt.getDebug().getResolvedrequest()).isEqualTo(bidRequest);

        verify(cacheService).cacheBidsOpenrtb(anyList(), anyList(), any(), any(), any());
    }

    private void givenCacheServiceResult(Map<Bid, CacheIdInfo> cacheBids) {
        givenCacheServiceResult(CacheServiceResult.of(null, null, cacheBids));
    }

    private void givenCacheServiceResult(CacheServiceResult cacheServiceResult) {
        given(cacheService.cacheBidsOpenrtb(any(), any(), any(), any(), any()))
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
        return ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, true, true);
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
