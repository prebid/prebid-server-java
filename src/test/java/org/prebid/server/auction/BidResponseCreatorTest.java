package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.MapUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.categorymapping.CategoryMappingService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.CategoryMappingResult;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.TargetingInfo;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.identity.IdGeneratorType;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtOptions;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtDebugPgmetrics;
import org.prebid.server.proto.openrtb.ext.response.ExtDebugTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountAuctionEventConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.settings.model.VideoStoredDataResult;
import org.prebid.server.vast.VastModifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source.xStatic;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category.pacing;
import static org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category.targeting;

public class BidResponseCreatorTest extends VertxTest {

    private static final BidRequestCacheInfo CACHE_INFO = BidRequestCacheInfo.builder().build();
    private static final Map<String, MultiBidConfig> MULTI_BIDS = emptyMap();

    private static final String IMP_ID = "impId1";
    private static final String BID_ADM = "adm";
    private static final String BID_NURL = "nurl";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheService cacheService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private VastModifier vastModifier;
    @Mock
    private EventsService eventsService;
    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private CategoryMappingService categoryMappingService;
    @Mock
    private HookStageExecutor hookStageExecutor;

    @Spy
    private WinningBidComparatorFactory winningBidComparatorFactory;

    private Clock clock;

    private Timeout timeout;

    private BidResponseCreator bidResponseCreator;

    @Before
    public void setUp() {
        given(cacheService.getEndpointHost()).willReturn("testHost");
        given(cacheService.getEndpointPath()).willReturn("testPath");
        given(cacheService.getCachedAssetURLTemplate()).willReturn("uuid=");

        given(categoryMappingService.createCategoryMapping(any(), any(), any()))
                .willAnswer(invocationOnMock -> Future.succeededFuture(
                        CategoryMappingResult.of(emptyMap(), emptyMap(), invocationOnMock.getArgument(0), null)));

        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any()))
                .willReturn(Future.succeededFuture(VideoStoredDataResult.empty()));

        given(idGenerator.getType()).willReturn(IdGeneratorType.none);

        given(hookStageExecutor.executeProcessedBidderResponseStage(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        BidderResponsePayloadImpl.of(((BidderResponse) invocation.getArgument(0))
                                .getSeatBid()
                                .getBids()))));
        given(hookStageExecutor.executeAllProcessedBidResponsesStage(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        HookStageExecutionResult.of(
                                false, AllProcessedBidResponsesPayloadImpl.of(invocation.getArgument(0)))));

        clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

        bidResponseCreator = givenBidResponseCreator(0);

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);
    }

    @Test
    public void shouldThrowErrorWhenTruncateAttrCharsLessThatZeroOrBiggestThatTwoHundredFiftyFive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> givenBidResponseCreator(-5))
                .withMessage("truncateAttrChars must be between 0 and 255");
    }

    @Test
    public void shouldPassBidWithGeneratedIdAndPreserveExtFieldsWhenIdGeneratorTypeUuid() {
        // given
        final Imp imp = givenImp();
        final String generatedId = "generatedId";
        given(idGenerator.getType()).willReturn(IdGeneratorType.uuid);
        given(idGenerator.generateId()).willReturn(generatedId);

        final ObjectNode receivedBidExt = mapper.createObjectNode()
                .put("origbidcur", "test");
        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .ext(receivedBidExt)
                .build();
        final String bidder = "bidder1";
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder, givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(imp),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS);

        // then
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder()
                .bidid(generatedId)
                .type(banner)
                .build();
        final Bid expectedBid = bid.toBuilder()
                .ext(mapper.createObjectNode()
                        .put("origbidcur", "test")
                        .set("prebid", mapper.valueToTree(extBidPrebid)))
                .build();
        final BidInfo bidInfo = toBidInfo(expectedBid, imp, bidder, banner, true);
        verify(cacheService).cacheBidsOpenrtb(eq(singletonList(bidInfo)), any(), any(), any());
    }

    @Test
    public void shouldSkipBidderWhenRejectedByProcessedBidderResponseHooks() {
        // given
        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)))
                .when(hookStageExecutor).executeProcessedBidderResponseStage(any(), any());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .isEmpty();
    }

    @Test
    public void shouldPassRequestModifiedByBidderRequestHooks() {
        doAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                false,
                BidderResponsePayloadImpl.of(singletonList(BidderBid.of(
                        Bid.builder()
                                .id("bidIdModifiedByHook")
                                .impid(IMP_ID)
                                .price(BigDecimal.valueOf(1.23))
                                .build(),
                        video,
                        "EUR"))))))
                .when(hookStageExecutor).executeProcessedBidderResponseStage(any(), any());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getId, Bid::getImpid, Bid::getPrice)
                .containsOnly(tuple("bidIdModifiedByHook", IMP_ID, BigDecimal.valueOf(1.23)));
    }

    @Test
    public void shouldPassOriginalTimeoutToCacheServiceIfCachingIsRequested() {
        // given
        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRequestCacheServiceWithExpectedArguments() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        final Bid bid3 = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(3.74)).build();
        final Bid bid4 = Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(6.74)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1",
                        givenSeatBid(
                                BidderBid.of(bid1, banner, "USD"),
                                BidderBid.of(bid2, banner, "USD")),
                        100),
                BidderResponse.of("bidder2",
                        givenSeatBid(
                                BidderBid.of(bid3, banner, "USD"),
                                BidderBid.of(bid4, banner, "USD")),
                        100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .cacheBidsTtl(99)
                .cacheVideoBidsTtl(101)
                .build();

        final Imp imp1 = givenImp("impId1");
        final Imp imp2 = givenImp("impId2");
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .events(mapper.createObjectNode())
                                .build())),
                        imp1, imp2),
                builder -> builder.account(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()))
                .with(toAuctionParticipant(bidderResponses));

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo,
                MULTI_BIDS);

        // then
        final ArgumentCaptor<CacheContext> contextArgumentCaptor = ArgumentCaptor.forClass(CacheContext.class);
        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(
                bidsArgumentCaptor.capture(),
                same(auctionContext),
                contextArgumentCaptor.capture(),
                eq(EventsContext.builder()
                        .auctionId("123")
                        .enabledForAccount(true)
                        .enabledForRequest(true)
                        .auctionTimestamp(1000L)
                        .build()));

        assertThat(bidsArgumentCaptor.getValue()).extracting(bidInfo -> bidInfo.getBid().getId())
                .containsOnly("bidId1", "bidId2", "bidId3", "bidId4");
        assertThat(contextArgumentCaptor.getValue())
                .satisfies(context -> {
                    assertThat(context.isShouldCacheBids()).isTrue();
                    assertThat(context.isShouldCacheVideoBids()).isTrue();
                    assertThat(context.getCacheBidsTtl()).isEqualTo(99);
                    assertThat(context.getCacheVideoBidsTtl()).isEqualTo(101);
                });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRequestCacheServiceWithWinningBidsOnlyWhenWinningonlyIsTrue() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(7.19)).build();
        final Bid bid3 = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(3.74)).build();
        final Bid bid4 = Bid.builder().id("bidId4").impid("impId2").price(BigDecimal.valueOf(6.74)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1",
                        givenSeatBid(
                                BidderBid.of(bid1, banner, "USD"),
                                BidderBid.of(bid2, banner, "USD")),
                        100),
                BidderResponse.of("bidder2",
                        givenSeatBid(
                                BidderBid.of(bid3, banner, "USD"),
                                BidderBid.of(bid4, banner, "USD")),
                        100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheWinningBidsOnly(true)
                .build();

        final Imp imp1 = givenImp("impId1");
        final Imp imp2 = givenImp("impId2");
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                imp1, imp2, givenImp("impId3"), givenImp("impId4")))
                .with(toAuctionParticipant(bidderResponses));

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo,
                MULTI_BIDS);

        // then
        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(
                bidsArgumentCaptor.capture(),
                same(auctionContext),
                any(),
                eq(EventsContext.builder().auctionId("123").auctionTimestamp(1000L).build()));

        assertThat(bidsArgumentCaptor.getValue()).extracting(bidInfo -> bidInfo.getBid().getId())
                .containsOnly("bidId1", "bidId2");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRequestCacheServiceWithVideoBidsToModify() {
        // given
        final String accountId = "accountId";
        final Account account = Account.builder()
                .id(accountId)
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();

        final String bidId1 = "bidId1";
        final Bid bid1 = Bid.builder()
                .id(bidId1)
                .impid("impId1")
                .price(BigDecimal.valueOf(5.67))
                .nurl(BID_NURL)
                .build();
        final Bid bid2 = Bid.builder()
                .id("bidId2")
                .impid("impId2")
                .price(BigDecimal.valueOf(7.19))
                .adm("adm2")
                .build();

        final String bidder1 = "bidder1";
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of(bidder1, givenSeatBid(BidderBid.of(bid1, video, "USD")), 100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid2, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheVideoBids(true)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.events(mapper.createObjectNode()),
                        givenImp("impId1"), givenImp("impId2")),
                contextBuilder -> contextBuilder.account(account))
                .with(toAuctionParticipant(bidderResponses));

        final String modifiedAdm = "modifiedAdm";
        given(vastModifier.createBidVastXml(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(modifiedAdm);

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS);

        // then
        final EventsContext expectedEventContext = EventsContext.builder()
                .auctionId("123")
                .enabledForAccount(true)
                .enabledForRequest(true)
                .auctionTimestamp(1000L)
                .build();

        verify(vastModifier)
                .createBidVastXml(eq(bidder1), eq(null), eq(BID_NURL), eq(bidId1),
                        eq(accountId), eq(expectedEventContext), eq(emptyList()), eq(null));

        final ArgumentCaptor<List<BidInfo>> bidInfoCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(
                bidInfoCaptor.capture(),
                same(auctionContext),
                eq(CacheContext.builder().shouldCacheVideoBids(true).build()),
                eq(expectedEventContext));

        assertThat(bidInfoCaptor.getValue())
                .extracting(bidInfo -> bidInfo.getBid().getId(), bidInfo -> bidInfo.getBid().getAdm())
                .containsOnly(tuple("bidId1", "modifiedAdm"), tuple("bidId2", "adm2"));
    }

    @Test
    public void shouldModifyBidAdmWhenBidVideoAndVastModifierReturnValue() {
        // given
        final String bidId = "bid_id";
        final Bid bid = Bid.builder()
                .id(bidId)
                .price(BigDecimal.ONE)
                .adm(BID_ADM)
                .nurl(BID_NURL)
                .impid(IMP_ID)
                .build();

        final String bidder = "bidder1";
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder, givenSeatBid(BidderBid.of(bid, video, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final String modifiedVast = "modifiedVast";
        given(vastModifier
                .createBidVastXml(anyString(), anyString(), anyString(),
                        anyString(), anyString(), any(), any(), any()))
                .willReturn(modifiedVast);

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsOnly(modifiedVast);

        verify(vastModifier)
                .createBidVastXml(eq(bidder), eq(BID_ADM), eq(BID_NURL), eq(bidId), eq("accountId"), any(), any(),
                        any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldCallCacheServiceEvenRoundedCpmIsZero() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(0.05)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()))
                .with(toAuctionParticipant(bidderResponses));

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS);

        // then
        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(
                bidsArgumentCaptor.capture(),
                same(auctionContext),
                eq(CacheContext.builder().build()),
                eq(EventsContext.builder().auctionId("123").auctionTimestamp(1000L).build()));

        assertThat(bidsArgumentCaptor.getValue()).extracting(bidInfo -> bidInfo.getBid().getId())
                .containsOnly("bidId1");
    }

    @Test
    public void shouldSetExpectedConstantResponseFields() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, null, MULTI_BIDS).result();

        // then
        final BidResponse responseWithExpectedFields = BidResponse.builder()
                .id("123")
                .cur("USD")
                .ext(ExtBidResponse.builder()
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.builder().auctiontimestamp(1000L).build())
                        .build())
                .build();

        assertThat(bidResponse)
                .usingRecursiveComparison()
                .ignoringFields("nbr", "seatbid")
                .isEqualTo(responseWithExpectedFields);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesAreEmpty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, null, MULTI_BIDS).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesDoNotContainAnyBids() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, null, MULTI_BIDS).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrNullAndPopulateSeatbidWhenAtLeastOneBidIsPresent() {
        // given
        final Bid bid = Bid.builder().impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, null)), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getNbr()).isNull();
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSkipBidderResponsesWhereSeatBidContainEmptyBids() {
        // given
        final Bid bid = Bid.builder().impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(), 0),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldOverrideBidIdWhenIdGeneratorIsUUID() {
        // given
        final Bid bid = Bid.builder()
                .id("123")
                .impid(IMP_ID)
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.valueToTree(ExtBidPrebid.builder().type(banner).build())))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(idGenerator.getType()).willReturn(IdGeneratorType.uuid);
        given(idGenerator.generateId()).willReturn("de7fc739-0a6e-41ad-8961-701c30c82166");

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("prebid"))
                .extracting(extPrebid -> mapper.treeToValue(extPrebid, ExtBidPrebid.class))
                .extracting(ExtBidPrebid::getBidid)
                .hasSize(1)
                .first()
                .isEqualTo("de7fc739-0a6e-41ad-8961-701c30c82166");

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldUseGeneratedBidIdForEventAndCacheWhenIdGeneratorIsUUIDAndEventEnabledForAccountAndRequest() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        final Imp imp = givenImp();

        // Allow events for request
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .events(mapper.createObjectNode())
                        .integration("pbjs"),
                imp);

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.ONE)
                .build();

        final String bidder = "bidder1";
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder, givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        final String generatedBidId = "de7fc739-0a6e-41ad-8961-701c30c82166";
        given(idGenerator.getType()).willReturn(IdGeneratorType.uuid);
        given(idGenerator.generateId()).willReturn(generatedBidId);

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, null, null)));
        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), anyString(), anyBoolean(), any()))
                .willReturn(events);

        // when
        bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().bidid(generatedBidId).type(banner).build();
        final Bid expectedBid = bid.toBuilder()
                .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(extBidPrebid)))
                .build();

        final BidInfo expectedBidInfo = toBidInfo(expectedBid, imp, bidder, banner, true);
        verify(cacheService).cacheBidsOpenrtb(eq(singletonList(expectedBidInfo)), any(), any(), any());

        verify(eventsService).createEvent(eq(generatedBidId), anyString(), anyString(), any(), anyBoolean(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSetExpectedResponseSeatBidAndBidFields() {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .put("origbidcpm", BigDecimal.ONE)
                .put("origbidcur", "USD");
        final Bid bid = Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .adm(BID_ADM)
                .impid(IMP_ID)
                .ext(bidExt)
                .build();

        final String bidder = "bidder1";
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(bidder,
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.set("prebid", mapper.valueToTree(ExtBidPrebid.builder().type(banner).build()));
        expectedBidExt.put("origbidcpm", BigDecimal.ONE);
        expectedBidExt.put("origbidcur", "USD");

        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(bidsArgumentCaptor.capture(), any(), any(), any());

        assertThat(bidsArgumentCaptor.getValue()).extracting(bidInfo -> bidInfo.getBid().getExt())
                .containsOnly(expectedBidExt);

        assertThat(bidResponse.getSeatbid())
                .containsOnly(SeatBid.builder()
                        .seat(bidder)
                        .group(0)
                        .bid(singletonList(Bid.builder()
                                .id("bidId")
                                .impid(IMP_ID)
                                .price(BigDecimal.ONE)
                                .adm(BID_ADM)
                                .ext(expectedBidExt)
                                .build()))
                        .build());
    }

    @Test
    public void shouldUpdateCacheDebugLogWithExtBidResponseWhenEnabledAndBidsReturned() {
        // given
        final BidRequest bidRequest = givenBidRequest(Imp.builder().id("i1").build());

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).adm("adm").impid("i1")
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .cachedDebugLog(new CachedDebugLog(true, 100, null, jacksonMapper))
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(auctionContext.getCachedDebugLog().buildCacheBody())
                .containsSequence("{\"responsetimemillis\":{\"bidder1\":100},\"tmaxrequest\":1000,"
                        + "\"prebid\":{\"auctiontimestamp\":1000}}</Response>");
    }

    @Test
    public void shouldUpdateCacheDebugLogWithExtBidResponseWhenEnabledAndNoBidsReturned() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(),
                contextBuilder -> contextBuilder.cachedDebugLog(new CachedDebugLog(true, 100, null, jacksonMapper)));

        // when
        bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(auctionContext.getCachedDebugLog().buildCacheBody())
                .containsSequence("{\"responsetimemillis\":{},\"tmaxrequest\":1000,\"prebid\""
                        + ":{\"auctiontimestamp\":1000}}");
    }

    @Test
    public void shouldUseBidsReturnedInCategoryMapperResultAndUpdateErrors() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(Imp.builder().id("i1").build())),
                extBuilder -> extBuilder.targeting(givenTargeting().toBuilder()
                        .includebrandcategory(ExtIncludeBrandCategory.of(null, null, null, null)).build()));

        final Bid bid1 = Bid.builder().id("bidId1").price(BigDecimal.ONE).adm("adm").impid("i1")
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final Bid bid2 = Bid.builder().id("bidId2").price(BigDecimal.ONE).adm("adm").impid("i1")
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid1, banner, "USD"), BidderBid.of(bid2, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(categoryMappingService.createCategoryMapping(any(), any(), any()))
                .willReturn(Future.succeededFuture(CategoryMappingResult.of(emptyMap(), emptyMap(),
                        singletonList(BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")),
                                100)),
                        singletonList("Filtered bid 2"))));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId1");

        assertThat(auctionContext.getPrebidErrors()).containsOnly("Filtered bid 2");
    }

    @Test
    public void shouldThrowExceptionWhenCategoryMappingThrowsPrebidException() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(identity(), identity(), givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(categoryMappingService.createCategoryMapping(any(), any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("category exception")));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("category exception");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNotWriteSkadnAttributeToBidderSection() {
        // given
        final Map<String, Object> bidExtProperties = new HashMap<>();
        bidExtProperties.put("skadn", singletonMap("skadnKey", "skadnValue"));
        bidExtProperties.put("origbidcur", "USD");
        final Bid bid = Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .adm(BID_ADM)
                .impid(IMP_ID)
                .ext(mapper.valueToTree(bidExtProperties))
                .build();

        final String bidder = "bidder1";
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(bidder,
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.set("prebid", mapper.valueToTree(ExtBidPrebid.builder().type(banner).build()));
        expectedBidExt.put("origbidcur", "USD");
        expectedBidExt.set("skadn", mapper.convertValue(singletonMap("skadnKey", "skadnValue"), JsonNode.class));

        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(bidsArgumentCaptor.capture(), any(), any(), any());

        assertThat(bidsArgumentCaptor.getValue()).extracting(bidInfo -> bidInfo.getBid().getExt())
                .containsOnly(expectedBidExt);

        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(expectedBidExt);
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
                .imp(singletonList(Imp.builder()
                        .id(IMP_ID)
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

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid(IMP_ID)
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

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

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
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
                        .id(IMP_ID)
                        .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                        .build()))
                .build();

        final Response responseAdm = Response.builder()
                .assets(singletonList(com.iab.openrtb.response.Asset.builder()
                        .id(123)
                        .img(com.iab.openrtb.response.ImageObject.builder().type(null).build())
                        .data(com.iab.openrtb.response.DataObject.builder().build())
                        .build()))
                .build();

        final Bid bid = Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .impid(IMP_ID)
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(adm -> mapper.readValue(adm, Response.class))
                .flatExtracting(Response::getAssets)
                .isEmpty();
    }

    @Test
    public void shouldReturnEmptyAssetIfNoRelatedNativeAssetFound() throws JsonProcessingException {
        // given
        final Request nativeRequest = Request.builder()
                .assets(singletonList(Asset.builder()
                        .id(null)
                        .img(ImageObject.builder().type(null).build())
                        .data(DataObject.builder().type(2).build())
                        .build()))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .tmax(1000L)
                .app(App.builder().build())
                .imp(singletonList(Imp.builder()
                        .id(IMP_ID)
                        .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                        .build()))
                .build();

        final Response responseAdm = Response.builder()
                .assets(singletonList(com.iab.openrtb.response.Asset.builder()
                        .id(123)
                        .img(com.iab.openrtb.response.ImageObject.builder().type(null).build())
                        .data(com.iab.openrtb.response.DataObject.builder().build())
                        .build()))
                .build();

        final Bid bid = Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .impid(IMP_ID)
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then

        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(adm -> mapper.readValue(adm, Response.class))
                .flatExtracting(Response::getAssets)
                .isEmpty();
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getErrors)
                .isEqualTo(Map.of("bidder1", singletonList(ExtBidderError.of(3,
                        "Response has an Image asset with ID:'123' present that doesn't exist in the request"))));
    }

    @Test
    public void shouldReturnEmptyAssetIfIdIsNotPresentRelatedNativeAssetFound() throws JsonProcessingException {
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
                        .id(IMP_ID)
                        .xNative(Native.builder().request(mapper.writeValueAsString(nativeRequest)).build())
                        .build()))
                .build();

        final Response responseAdm = Response.builder()
                .assets(singletonList(com.iab.openrtb.response.Asset.builder()
                        .id(null)
                        .img(com.iab.openrtb.response.ImageObject.builder().type(null).build())
                        .data(com.iab.openrtb.response.DataObject.builder().build())
                        .build()))
                .build();

        final Bid bid = Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .impid(IMP_ID)
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then

        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getAdm)
                .extracting(adm -> mapper.readValue(adm, Response.class))
                .flatExtracting(Response::getAssets)
                .isEmpty();
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getErrors)
                .isEqualTo(Map.of("bidder1", singletonList(ExtBidderError.of(3,
                        "Response has an Image asset with ID:'' present that doesn't exist in the request"))));
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
                        .id(IMP_ID)
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

        final Bid bid = Bid.builder().id("bidId")
                .price(BigDecimal.ONE)
                .impid(IMP_ID)
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final Bid bid = Bid.builder()
                .price(BigDecimal.ONE)
                .adm(BID_ADM)
                .id("bidId")
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, null, null)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetBidAdmToNullIfVideoCacheIdIsPresentAndReturnCreativeVideoBidsIsFalse() {
        // given
        final Bid bid = Bid.builder().price(BigDecimal.ONE).adm(BID_ADM).id("bidId").impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetBidExpWhenCacheIdIsMatched() {
        // given
        final Bid bid = Bid.builder().price(BigDecimal.ONE).impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp(Collections.singletonMap("dealId", "lineItemId1"))),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, 100, null)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getExp)
                .containsOnly(100);

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetBidExpMaxTtlWhenCacheIdIsMatchedAndBothTtlIsSet() {
        // given
        final Bid bid = Bid.builder().price(BigDecimal.ONE).impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, 100, 200)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getExp)
                .containsOnly(200);

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        final ObjectNode expectedBidExt = mapper.createObjectNode();
        expectedBidExt.set("prebid", mapper.valueToTree(ExtBidPrebid.builder().type(banner).build()));
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .containsOnly(Bid.builder()
                        .id("bidId")
                        .impid(IMP_ID)
                        .price(BigDecimal.ONE)
                        .ext(expectedBidExt)
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldPassPreferDealsToWinningComparatorFactoryWhenBidRequestTrue() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        bidRequestBuilder -> bidRequestBuilder.imp(singletonList(givenImp("i1"))),
                        extBuilder -> extBuilder.targeting(givenTargeting().toBuilder().preferdeals(true).build())),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        verify(winningBidComparatorFactory, times(2)).create(eq(true));
    }

    @Test
    public void shouldPassPreferDealsFalseWhenBidRequestPreferDealsIsNotDefined() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        bidRequestBuilder -> bidRequestBuilder.imp(singletonList(givenImp("i1"))),
                        extBuilder -> extBuilder.targeting(givenTargeting())),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        verify(winningBidComparatorFactory, times(2)).create(eq(false));
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_bidder_bidder1", "bidder1"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldTruncateTargetingKeywordsByGlobalConfig() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidResponseCreator bidResponseCreator = new BidResponseCreator(
                cacheService,
                bidderCatalog,
                vastModifier,
                eventsService,
                storedRequestProcessor,
                winningBidComparatorFactory,
                idGenerator,
                hookStageExecutor,
                categoryMappingService,
                20,
                clock,
                jacksonMapper);

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_someVeryLongBi", "5.00"),
                        tuple("hb_bidder", "someVeryLongBidderName"),
                        tuple("hb_bidder_someVeryLo", "someVeryLongBidderName"),
                        tuple("hb_bidder_someVeryLo", "someVeryLongBidderName"));
    }

    @Test
    public void shouldTruncateTargetingKeywordsByAccountConfig() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .truncateTargetAttr(20)
                        .build())
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp());

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
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
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .truncateTargetAttr(25)
                        .build())
                .build();
        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(
                        ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))))
                .includewinners(true)
                .includebidderkeys(true)
                .includeformat(false)
                .truncateattrchars(20)
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(targeting),
                givenImp());

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_someVeryLongBi", "5.00"),
                        tuple("hb_bidder", "someVeryLongBidderName"),
                        tuple("hb_bidder_someVeryLo", "someVeryLongBidderName"));
    }

    @Test
    public void shouldReduceAndNotPopulateTargetingKeywordsForExtraBidsWhenCodePrefixIsNotDefined() {
        // given
        final String bidder1 = "bidder1";
        final Map<String, MultiBidConfig> multiBidMap = singletonMap(bidder1, MultiBidConfig.of(bidder1, 3, null));

        final Bid bidder1Bid1 = Bid.builder().id("bidder1Bid1").price(BigDecimal.valueOf(3.67)).impid("i1").build();
        final Bid bidder1Bid2 = Bid.builder().id("bidder1Bid2").price(BigDecimal.valueOf(4.98)).impid("i1").build();
        final Bid bidder1Bid3 = Bid.builder().id("bidder1Bid3").price(BigDecimal.valueOf(1.08)).impid("i1").build();
        final Bid bidder1Bid4 = Bid.builder().id("bidder1Bid4").price(BigDecimal.valueOf(11.8)).impid("i1").build();
        final Bid bidder1Bid5 = Bid.builder().id("bidder1Bid5").price(BigDecimal.valueOf(1.08)).impid("i2").build();

        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder1,
                        givenSeatBid(
                                BidderBid.of(bidder1Bid1, banner, null),  // extra bid
                                BidderBid.of(bidder1Bid2, banner, null),  // extra bid
                                BidderBid.of(bidder1Bid3, banner, null),  // Will be removed by price
                                BidderBid.of(bidder1Bid4, banner, null),
                                BidderBid.of(bidder1Bid5, banner, null)),
                        100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp("i1"), givenImp("i2")),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse result = bidResponseCreator.create(auctionContext, CACHE_INFO, multiBidMap).result();

        // then
        assertThat(result.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(4)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"),
                        BidResponseCreatorTest::getTargetingBidderCode)
                .containsOnly(
                        tuple("bidder1Bid4", bidder1, bidder1, bidder1),
                        tuple("bidder1Bid2", null, null, null),
                        tuple("bidder1Bid1", null, null, null),
                        tuple("bidder1Bid5", bidder1, bidder1, null));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateTargetingKeywordsForExtraBidsWhenCodePrefixIsDefinedAndBidderKeysFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))))
                        .includewinners(true)
                        .includebidderkeys(false)
                        .includeformat(false)
                        .build()),
                givenImp("i1"));

        final String bidder1 = "bidder1";
        final Map<String, MultiBidConfig> multiBidMap = singletonMap(bidder1, MultiBidConfig.of(bidder1, 3, "pref"));

        final Bid bidder1Bid1 = Bid.builder().id("bidder1Bid1").price(BigDecimal.valueOf(3.67)).impid("i1").build();
        final Bid bidder1Bid2 = Bid.builder().id("bidder1Bid2").price(BigDecimal.valueOf(4.98)).impid("i1").build();
        final Bid bidder1Bid3 = Bid.builder().id("bidder1Bid3").price(BigDecimal.valueOf(1.08)).impid("i1").build();

        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder1,
                        givenSeatBid(
                                BidderBid.of(bidder1Bid1, banner, null),  // extra bid
                                BidderBid.of(bidder1Bid2, banner, null),  // extra bid
                                BidderBid.of(bidder1Bid3, banner, null)),
                        100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse result = bidResponseCreator.create(auctionContext, CACHE_INFO, multiBidMap).result();

        final Map<String, String> expectedWinningBidTargetingMap = new HashMap<>();
        expectedWinningBidTargetingMap.put("hb_pb", "4.50");
        expectedWinningBidTargetingMap.put("hb_bidder", bidder1);

        // then
        assertThat(result.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(
                        Bid::getId,
                        bid -> toExtBidPrebid(bid.getExt()).getTargeting())
                .containsOnly(
                        tuple("bidder1Bid2", expectedWinningBidTargetingMap),
                        tuple("bidder1Bid1", null),
                        tuple("bidder1Bid3", null));
    }

    @Test
    public void shouldReduceAndPopulateTargetingKeywordsForExtraBidsWhenCodePrefixIsDefined() {
        // given
        final String bidder1 = "bidder1";
        final String codePrefix = "bN";
        final Map<String, MultiBidConfig> multiBidMap = singletonMap(bidder1,
                MultiBidConfig.of(bidder1, 3, codePrefix));

        final Bid bidder1Bid1 = Bid.builder().id("bidder1Bid1").price(BigDecimal.valueOf(3.67)).impid("i1").build();
        final Bid bidder1Bid2 = Bid.builder().id("bidder1Bid2").price(BigDecimal.valueOf(4.88)).impid("i1").build();
        final Bid bidder1Bid3 = Bid.builder().id("bidder1Bid3").price(BigDecimal.valueOf(1.08)).impid("i1").build();
        final Bid bidder1Bid4 = Bid.builder().id("bidder1Bid4").price(BigDecimal.valueOf(11.8)).impid("i1").build();
        final Bid bidder1Bid5 = Bid.builder().id("bidder1Bid5").price(BigDecimal.valueOf(1.08)).impid("i2").build();

        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder1,
                        givenSeatBid(
                                BidderBid.of(bidder1Bid1, banner, null),  // extra bid
                                BidderBid.of(bidder1Bid2, banner, null),  // extra bid
                                BidderBid.of(bidder1Bid3, banner, null),  // Will be removed by price
                                BidderBid.of(bidder1Bid4, banner, null),
                                BidderBid.of(bidder1Bid5, banner, null)),
                        100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp("i1"), givenImp("i2")),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse result = bidResponseCreator.create(auctionContext, CACHE_INFO, multiBidMap).result();

        // then
        final Map<String, String> bidder1Bid4Targeting = new HashMap<>();
        bidder1Bid4Targeting.put("hb_pb", "5.00");
        bidder1Bid4Targeting.put("hb_pb_" + bidder1, "5.00");
        bidder1Bid4Targeting.put("hb_bidder_" + bidder1, bidder1);
        bidder1Bid4Targeting.put("hb_bidder", bidder1);
        final ObjectNode bidder1Bid4Ext = extWithTargeting(bidder1, bidder1Bid4Targeting);
        final Bid expectedBidder1Bid4 = bidder1Bid4.toBuilder().ext(bidder1Bid4Ext).build();

        final String bidderCodeForBidder1Bid2 = codePrefix + 2;
        final Map<String, String> bidder1Bid2Targeting = new HashMap<>();
        bidder1Bid2Targeting.put("hb_bidder_" + bidderCodeForBidder1Bid2, bidderCodeForBidder1Bid2);
        bidder1Bid2Targeting.put("hb_pb_" + bidderCodeForBidder1Bid2, "4.50");
        final ObjectNode bidder1Bid2Ext = extWithTargeting(bidderCodeForBidder1Bid2, bidder1Bid2Targeting);
        final Bid expectedBidder1Bid2 = bidder1Bid2.toBuilder().ext(bidder1Bid2Ext).build();

        final String bidderCodeForBidder1Bid1 = codePrefix + 3;
        final Map<String, String> bidder1Bid1Targeting = new HashMap<>();
        bidder1Bid1Targeting.put("hb_bidder_" + bidderCodeForBidder1Bid1, bidderCodeForBidder1Bid1);
        bidder1Bid1Targeting.put("hb_pb_" + bidderCodeForBidder1Bid1, "3.50");
        final ObjectNode bidder1Bid1Ext = extWithTargeting(bidderCodeForBidder1Bid1, bidder1Bid1Targeting);
        final Bid expectedBidder1Bid1 = bidder1Bid1.toBuilder().ext(bidder1Bid1Ext).build();

        final Map<String, String> bidder1Bid5Targeting = new HashMap<>();
        bidder1Bid5Targeting.put("hb_pb", "1.00");
        bidder1Bid5Targeting.put("hb_pb_" + bidder1, "1.00");
        bidder1Bid5Targeting.put("hb_bidder_" + bidder1, bidder1);
        bidder1Bid5Targeting.put("hb_bidder", bidder1);
        final ObjectNode bidder1Bid5Ext = extWithTargeting(null, bidder1Bid5Targeting);
        final Bid expectedBidder1Bid5 = bidder1Bid5.toBuilder().ext(bidder1Bid5Ext).build();

        assertThat(result.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .contains(expectedBidder1Bid4, expectedBidder1Bid2, expectedBidder1Bid1, expectedBidder1Bid5);
        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsForWinningBidsAndWinningBidsByBidder() {
        // given
        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();
        final Bid secondBid = Bid.builder().id("bidId2").price(BigDecimal.valueOf(4.98)).impid("i2").build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(7.25)).impid("i2").build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1",
                        givenSeatBid(
                                BidderBid.of(firstBid, banner, null),
                                BidderBid.of(secondBid, banner, null)), 100),
                BidderResponse.of("bidder2",
                        givenSeatBid(BidderBid.of(thirdBid, banner, null)), 111));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp("i1"), givenImp("i2")),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

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

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldPopulateAuctionLostToMetricByWinningDealBid() {
        // given
        final String dealId1 = "dealId1";
        final String dealId2 = "dealId2";
        final String lineItemId1 = "lineItemId1";
        final String lineItemId2 = "lineItemId2";
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                Imp.builder()
                        .id(IMP_ID)
                        .pmp(Pmp.builder()
                                // Order defines winning bid
                                .deals(asList(
                                        Deal.builder().id("dealId1")
                                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                                        "lineItemId1", null, null, null)))).build(),
                                        Deal.builder().id("dealId2")
                                                .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                                        "lineItemId2", null, null, null)))).build()))
                                .build())
                        .build());

        final Bid firstBid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67))
                .dealid(dealId1).build();
        final Bid secondBid = Bid.builder().id("bidId2").impid(IMP_ID).price(BigDecimal.valueOf(4.98))
                .dealid(dealId2).build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(firstBid, banner, null)), 100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(secondBid, banner, null)), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(auctionContext.getTxnLog().lostAuctionToLineItems().entrySet())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(lineItemId2, singleton(lineItemId1)));
    }

    @Test
    public void shouldIncreaseLineItemSentToClientAsTopMatchMetricInTransactionLog() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp(Collections.singletonMap("dealId", "lineItemId1")));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .dealid("dealId")
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, null)), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                context -> context
                        .debugContext(DebugContext.of(true, true, null))
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        final ExtBidResponse responseExt = bidResponse.getExt();

        assertThat(responseExt.getDebug()).isNotNull();
        assertThat(responseExt.getDebug().getPgmetrics()).isNotNull();
        assertThat(singletonList(responseExt.getDebug().getPgmetrics()))
                .flatExtracting(ExtDebugPgmetrics::getSentToClientAsTopMatch)
                .containsOnly("lineItemId1");
    }

    @Test
    public void shouldPopulateTargetingKeywordsFromMediaTypePriceGranularities() {
        // given
        final ExtMediaTypePriceGranularity extMediaTypePriceGranularity = ExtMediaTypePriceGranularity.of(
                mapper.valueToTree(ExtPriceGranularity.of(
                        3,
                        singletonList(ExtGranularityRange.of(
                                BigDecimal.valueOf(10), BigDecimal.valueOf(1))))),
                null,
                null);
        final ExtPriceGranularity extPriceGranularity = ExtPriceGranularity.of(2,
                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))));

        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(extPriceGranularity))
                .mediatypepricegranularity(extMediaTypePriceGranularity)
                .includewinners(true)
                .includebidderkeys(true)
                .includeformat(false)
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(targeting),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.000"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_pb_bidder1", "5.000"),
                        tuple("hb_bidder_bidder1", "bidder1"));

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldPopulateCacheIdHostPathAndUuidTargetingKeywords() {
        // given
        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
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

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldPopulateTargetingKeywordsWithAdditionalValuesFromRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .targeting(givenTargeting())
                        .adservertargeting(singletonList(ExtRequestPrebidAdservertargetingRule.of(
                                "static_keyword1", xStatic, "static_keyword1"))),
                givenImp());

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("static_keyword1", "static_keyword1"));
    }

    @Test
    public void shouldPopulateTargetingKeywordsIfBidWasCachedAndAdmWasRemoved() {
        // given
        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).adm(BID_ADM).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .returnCreativeBids(false) // this will cause erasing of bid.adm
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", null, null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // Check if you didn't lost any bids because of bid change in winningBids set
        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .doesNotContainNull();
    }

    @Test
    public void shouldCallEventsServiceWhenEventsDisabledByRequestButBidWithLineItem() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp(Collections.singletonMap("dealId", "lineItemId")));

        final Bid bid = Bid.builder()
                .id("bidId")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .dealid("dealId")
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        verify(eventsService).createEvent(
                anyString(), anyString(), anyString(), eq("lineItemId"), eq(false), any());
    }

    @Test
    public void shouldAddExtPrebidEventsIfEventsAreEnabledAndExtRequestPrebidEventPresent() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .events(mapper.createObjectNode())
                        .integration("pbjs"),
                givenImp());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .willReturn(events);

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(bidsArgumentCaptor.capture(), any(), any(), any());

        assertThat(bidsArgumentCaptor.getValue())
                .extracting(bidInfo -> toExtBidPrebid(bidInfo.getBid().getExt()).getEvents())
                .containsOnly(events);

        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsOnly(events);
    }

    @Test
    public void shouldAddExtPrebidEventsIfEventsAreEnabledAndAccountSupportEventsForChannel() {
        // given
        final AccountAuctionEventConfig eventsConfig = AccountAuctionEventConfig.builder().build();
        eventsConfig.addEvent("web", true);
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .analytics(AccountAnalyticsConfig.of(eventsConfig, null))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .channel(ExtRequestPrebidChannel.of("web"))
                        .integration("pbjs"),
                givenImp());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .willReturn(events);

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsOnly(events);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldAddExtPrebidEventsIfEventsAreEnabledAndDefaultAccountAnalyticsConfig() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .analytics(AccountAnalyticsConfig.of(null, singletonMap("some-analytics", mapper.createObjectNode())))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .channel(ExtRequestPrebidChannel.of("amp"))
                        .integration("pbjs"),
                givenImp());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .willReturn(events);

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsOnly(events);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldAddExtPrebidVideoToExtBidPrebidWhenVideoBids() {
        // given
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(1, "category")).build();
        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(extBidPrebid)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(bidsArgumentCaptor.capture(), any(), any(), any());

        assertThat(bidsArgumentCaptor.getValue())
                .extracting(bidInfo -> toExtBidPrebid(bidInfo.getBid().getExt()).getVideo())
                .containsOnly(ExtBidPrebidVideo.of(1, "category"));

        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getVideo())
                .containsOnly(ExtBidPrebidVideo.of(1, "category"));
    }

    @Test
    public void shouldAddDealTierSatisfiedToExtBidPrebidWhenBidsPrioritySatisfiedMinPriority() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").impid("i1")
                .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(
                        ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(1, "category")).build()))).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(Imp.builder().id("i1").build()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(categoryMappingService.createCategoryMapping(any(), any(), any()))
                .willReturn(Future.succeededFuture(CategoryMappingResult.of(emptyMap(),
                        Collections.singletonMap(bid, true),
                        bidderResponses, emptyList())));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("prebid"))
                .extracting(ext -> mapper.convertValue(ext, ExtBidPrebid.class))
                .extracting(ExtBidPrebid::getDealTierSatisfied)
                .containsOnly(true);
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfEventsAreNotEnabled() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(false))
                        .build())
                .build();

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.events(mapper.createObjectNode()),
                        givenImp()),
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsNull();
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfExtRequestPrebidEventsNull() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsNull();
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfAccountDoesNotSupportEventsForChannel() {
        // given
        final AccountAuctionEventConfig eventsConfig = AccountAuctionEventConfig.builder().build();
        eventsConfig.addEvent("web", true);
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .analytics(AccountAnalyticsConfig.of(eventsConfig, null))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.channel(ExtRequestPrebidChannel.of("amp")),
                givenImp());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsNull();
    }

    @Test
    public void shouldReturnCacheEntityInExt() {
        // given
        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(givenTargeting()),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getCache())
                .extracting(ExtResponseCache::getBids, ExtResponseCache::getVastXml)
                .containsExactly(tuple(
                        CacheAsset.of("uuid=cacheId", "cacheId"),
                        CacheAsset.of("uuid=videoId", "videoId")));

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateWinningBidTargetingIfIncludeWinnersFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))))
                        .includewinners(false)
                        .includebidderkeys(true)
                        .includeformat(false)
                        .build()),
                givenImp());

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder"),
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", null, "bidder1"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateBidderKeysTargetingIfIncludeBidderKeysFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))))
                        .includewinners(true)
                        .includebidderkeys(false)
                        .includeformat(false)
                        .build()),
                givenImp());

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder"),
                        extractedBid -> toTargetingByKey(extractedBid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", "bidder1", null));

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldNotPopulateCacheIdTargetingKeywordsIfBidCpmIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp("impId1"), givenImp("impId2"));

        final Bid firstBid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid secondBid = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(firstBid, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(secondBid, banner, null)), 123));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId2", null, null, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).hasSize(2)
                .extracting(
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_cache_id"),
                        bid -> toTargetingByKey(bid, "hb_cache_id_bidder2"))
                .containsOnly(
                        tuple("bidder1", null, null),
                        tuple("bidder2", "cacheId2", "cacheId2"));

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNotCacheNonDealBidWithCpmIsZeroAndCacheDealBidWithZeroCpm() {
        // given
        final Imp imp1 = givenImp("impId1");
        final Imp imp2 = givenImp("impId2");

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.ZERO).dealid("dealId2").build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid2, banner, null)), 99));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(imp1, imp2),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId2", null, null, null)));

        // when
        bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(bidsArgumentCaptor.capture(), any(), any(), any());

        assertThat(bidsArgumentCaptor.getValue()).extracting(bidInfo -> bidInfo.getBid().getId())
                .containsOnly("bidId2");
    }

    @Test
    public void shouldPopulateBidResponseExtension() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .tmax(1000L)
                .app(App.builder().build())
                .imp(singletonList(givenImp()))
                .build();

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).adm("[]").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "bidder1",
                BidderSeatBid.builder()
                        .bids(singletonList(BidderBid.of(bid, xNative, null)))
                        .errors(singletonList(BidderError.badInput("bad_input")))
                        .warnings(singletonList(BidderError.generic("some_warning")))
                        .build(),
                100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(CacheServiceResult.of(
                DebugHttpCall.builder().endpoint("http://cache-service/cache").responseTimeMillis(666).build(),
                new RuntimeException("cacheError"), emptyMap()));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ExtBidResponse responseExt = bidResponse.getExt();

        assertThat(responseExt.getDebug()).isNull();
        assertThat(responseExt.getWarnings())
                .containsEntry("bidder1", singletonList(ExtBidderError.of(999, "some_warning")));
        assertThat(responseExt.getUsersync()).isNull();
        assertThat(responseExt.getTmaxrequest()).isEqualTo(1000L);

        assertThat(responseExt.getErrors()).hasSize(2).containsOnly(
                entry("bidder1", asList(
                        ExtBidderError.of(2, "bad_input"),
                        ExtBidderError.of(3, "Failed to decode: Cannot deserialize value"
                                + " of type `com.iab.openrtb.response.Response` from Array value "
                                + "(token `JsonToken.START_ARRAY`)\n"
                                + " at [Source: (String)\"[]\"; line: 1, column: 1]"))),
                entry("cache", singletonList(ExtBidderError.of(999, "cacheError"))));

        assertThat(responseExt.getResponsetimemillis()).hasSize(2)
                .containsOnly(entry("bidder1", 100), entry("cache", 666));

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void impToStoredVideoJsonShouldTolerateWhenStoredVideoFetchIsFailed() {
        // given
        final Imp imp = Imp.builder().id(IMP_ID).ext(
                        mapper.valueToTree(
                                ExtImp.of(
                                        ExtImpPrebid.builder()
                                                .storedrequest(ExtStoredRequest.of("st1"))
                                                .options(ExtOptions.of(true))
                                                .build(),
                                        null
                                )))
                .build();

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(imp),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any())).willReturn(
                Future.failedFuture("Fetch failed"));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(any(), eq(singletonList(imp)), anyList(), eq(timeout));

        assertThat(result.succeeded()).isTrue();
    }

    @SuppressWarnings("unchecked")
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
        final List<BidderBid> bidderBids = mutableList(
                BidderBid.of(bid1, banner, "USD"),
                BidderBid.of(bid2, banner, "USD"),
                BidderBid.of(bid3, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        final Video storedVideo = Video.builder().maxduration(100).h(2).w(2).build();
        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any()))
                .willReturn(Future.succeededFuture(
                        VideoStoredDataResult.of(singletonMap("impId1", storedVideo), emptyList())));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final Future<BidResponse> result = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(any(), eq(asList(imp1, imp3)), anyList(),
                eq(timeout));

        final ArgumentCaptor<List<BidInfo>> bidsArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).cacheBidsOpenrtb(bidsArgumentCaptor.capture(), any(), any(), any());

        assertThat(bidsArgumentCaptor.getValue())
                .extracting(bidInfo -> toExtBidPrebid(bidInfo.getBid().getExt()).getStoredRequestAttributes())
                .containsOnly(storedVideo, null, null);

        assertThat(result.result().getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getStoredRequestAttributes())
                .containsOnly(storedVideo, null, null);
    }

    @Test
    public void impToStoredVideoJsonShouldAddErrorsWithPrebidBidderWhenStoredVideoRequestFailed() {
        // given
        final Imp imp1 = Imp.builder().id(IMP_ID).ext(
                        mapper.valueToTree(
                                ExtImp.of(ExtImpPrebid.builder()
                                                .storedrequest(ExtStoredRequest.of("st1"))
                                                .options(ExtOptions.of(true))
                                                .build(),
                                        null)))
                .build();
        final BidRequest bidRequest = givenBidRequest(imp1);

        final Bid bid1 = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any()))
                .willReturn(Future.failedFuture("Bad timeout"));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(any(), eq(singletonList(imp1)), anyList(), eq(timeout));

        assertThat(result.result().getExt()).isEqualTo(
                ExtBidResponse.builder()
                        .errors(singletonMap("prebid",
                                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(), "Bad timeout"))))
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.builder().auctiontimestamp(1000L).build())
                        .build());
    }

    @Test
    public void shouldProcessRequestAndAddErrorAboutDeprecatedBidder() {
        // given
        final String invalidBidderName = "invalid";

        final BidRequest bidRequest = givenBidRequest(Imp.builder()
                .ext(mapper.valueToTree(
                        ExtImp.of(ExtImpPrebid.builder()
                                .bidder(mapper.valueToTree(singletonMap(invalidBidderName, 0)))
                                .build(),
                        null)
                ))
                .build());

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        given(bidderCatalog.isDeprecatedName(invalidBidderName)).willReturn(true);
        given(bidderCatalog.errorForDeprecatedName(invalidBidderName)).willReturn(
                "invalid has been deprecated and is no longer available. Use valid instead.");

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt()).isEqualTo(
                ExtBidResponse.builder()
                        .errors(singletonMap(invalidBidderName,
                                singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                        "invalid has been deprecated and is no longer available. Use valid instead."))))
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.builder().auctiontimestamp(1000L).build())
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldProcessRequestAndAddErrorFromAuctionContext() {
        // given
        final Bid bid1 = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder
                        .prebidErrors(singletonList("privacy error"))
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        assertThat(result.result().getExt()).isEqualTo(
                ExtBidResponse.builder()
                        .errors(singletonMap(
                                "prebid",
                                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(), "privacy error"))))
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.builder().auctiontimestamp(1000L).build())
                        .build());
    }

    @Test
    public void shouldPopulateResponseDebugExtensionAndWarningsIfDebugIsEnabled() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());
        final List<String> warnings = asList("warning1", "warning2");

        givenCacheServiceResult(CacheServiceResult.of(
                DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestUri("test.uri")
                        .responseStatus(500)
                        .build(),
                null,
                emptyMap()));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "bidder1",
                BidderSeatBid.builder()
                        .bids(singletonList(BidderBid.of(bid, banner, null)))
                        .httpCalls(singletonList(ExtHttpCall.builder().status(200).build()))
                        .build(),
                100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                builder -> builder
                        .debugWarnings(warnings)
                        .debugContext(DebugContext.of(true, true, null))
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        auctionContext.getDebugHttpCalls().put("userservice", singletonList(
                DebugHttpCall.builder()
                        .requestUri("userservice.uri")
                        .responseStatus(500)
                        .responseTimeMillis(200)
                        .build()));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ExtBidResponse responseExt = bidResponse.getExt();

        assertThat(responseExt.getDebug()).isNotNull();
        assertThat(responseExt.getDebug().getHttpcalls()).hasSize(3)
                .containsOnly(
                        entry("bidder1", singletonList(ExtHttpCall.builder().status(200).build())),
                        entry("cache", singletonList(ExtHttpCall.builder().uri("test.uri").status(500).build())),
                        entry("userservice", singletonList(ExtHttpCall.builder().uri("userservice.uri").status(500)
                                .build())));

        assertThat(responseExt.getDebug().getResolvedrequest()).isEqualTo(bidRequest);

        assertThat(responseExt.getWarnings())
                .containsOnly(
                        entry("prebid", Arrays.asList(
                                ExtBidderError.of(BidderError.Type.generic.getCode(), "warning1"),
                                ExtBidderError.of(BidderError.Type.generic.getCode(), "warning2"))));

        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldPassIntegrationToCacheServiceAndBidEvents() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .cur(singletonList("USD"))
                .imp(singletonList(givenImp()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .events(mapper.createObjectNode())
                        .integration("integration")
                        .build()))
                .build();

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        given(eventsService.createEvent(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .willReturn(Events.of(
                        "http://win-url?param=value&int=integration",
                        "http://imp-url?param=value&int=integration"));

        // when
        final Future<BidResponse> result = bidResponseCreator.create(auctionContext, cacheInfo, MULTI_BIDS);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(),
                argThat(eventsContext -> eventsContext.getIntegration().equals("integration")));

        assertThat(result.result().getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getEvents())
                .containsOnly(Events.of(
                        "http://win-url?param=value&int=integration",
                        "http://imp-url?param=value&int=integration"));
    }

    @Test
    public void shouldPopulateExtensionResponseDebugAndDeepDebugLogIfEnabled() {
        // given
        final DeepDebugLog deepDebugLog = DeepDebugLog.create(true, clock);
        deepDebugLog.add("line-item-id-1", pacing, () -> "test-1");
        deepDebugLog.add("line-item-id-2", targeting, () -> "test-2");
        deepDebugLog.add("", targeting, () -> "test-3");

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                builder -> builder
                        .deepDebugLog(deepDebugLog)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        final ExtDebugTrace extDebugTrace = bidResponse.getExt().getDebug().getTrace();

        assertThat(extDebugTrace.getDeals())
                .containsExactly(ExtTraceDeal.of("", ZonedDateTime.now(clock), targeting, "test-3"));

        assertThat(extDebugTrace.getLineItems())
                .containsExactly(
                        entry("line-item-id-1", List.of(ExtTraceDeal.of("line-item-id-1",
                                ZonedDateTime.now(clock), pacing, "test-1"))),
                        entry("line-item-id-2", List.of(ExtTraceDeal.of("line-item-id-2",
                                ZonedDateTime.now(clock), targeting, "test-2"))));
    }

    @Test
    public void shouldBidResponseDebugReturnNullIfDeepDebugLogIsEnabledAndNotPopulated() {
        // given
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                builder -> builder
                        .deepDebugLog(DeepDebugLog.create(true, clock))
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt().getDebug()).isNull();
    }

    @Test
    public void shouldPopulateBidResponseExtErrorIfImpExtIsInvalid() {
        // given
        final String errorMessage = "Incorrect Imp extension format for Imp with id imp-test: Cannot deserialize";
        given(storedRequestProcessor.videoStoredDataResult(anyString(), anyList(), anyList(), any()))
                .willReturn(Future.succeededFuture(VideoStoredDataResult.of(emptyMap(), List.of(errorMessage))));

        final BidRequest bidRequest = givenBidRequest(Imp.builder()
                .ext(mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                        .set("storedrequest", mapper.createObjectNode()
                                .set("id", mapper.createObjectNode().putArray("id")
                                        .add("id"))))).id(IMP_ID).build());

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt().getErrors().get("prebid").get(0).getMessage()).isEqualTo(errorMessage);
    }

    @Test
    public void shouldThrowErrorIfBidIdAndCorrespondingImpIdNotEquals() {
        // given
        final BidRequest bidRequest = givenBidRequest(Imp.builder().id("312").build());

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("123").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final Future<BidResponse> bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS);

        // when
        assertThat(bidResponse.failed()).isTrue();
        assertThat(bidResponse.cause()).hasMessage("Bid with impId 123 doesn't have matched imp");
    }

    @Test
    public void shouldThrowExceptionWhenBidAdmIsParsedButImpNativeNotFound() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .price(BigDecimal.valueOf(5.67))
                .nurl(BID_NURL)
                .adm(mapper.writeValueAsString(Response.builder()
                        .assets(List.of(com.iab.openrtb.response.Asset.builder().build()))
                        .build()))
                .build();

        final String bidder1 = "bidder1";
        final List<BidderResponse> bidderResponses = List.of(BidderResponse.of(bidder1,
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp("impId1")),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getErrors)
                .extracting(error -> error.get(bidder1))
                .extracting(extBidderErrors -> extBidderErrors.get(0))
                .isEqualTo(ExtBidderError.of(3, "Could not find native imp"));

    }

    @Test
    public void shouldThrowExceptionWhenNativeRequestIsInvalid() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .price(BigDecimal.valueOf(5.67))
                .nurl(BID_NURL)
                .adm(mapper.writeValueAsString(Response.builder()
                        .assets(List.of(com.iab.openrtb.response.Asset.builder().build()))
                        .build()))
                .build();

        final String bidder1 = "bidder1";
        final List<BidderResponse> bidderResponses = List.of(BidderResponse.of(bidder1,
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final ObjectNode customObjectNode = mapper.createObjectNode();
        customObjectNode.set("test-field", mapper
                .createObjectNode().set("other-test-field", mapper
                        .createArrayNode().add(22).add(33)));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(Imp.builder()
                        .id("impId1")
                        .xNative(Native.builder().request(customObjectNode.asText()).build())
                        .build()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getErrors)
                .extracting(error -> error.get(bidder1))
                .extracting(extBidderErrors -> extBidderErrors.get(0))
                .isEqualTo(ExtBidderError.of(3, "No content to map due to end-of-input\n"
                        + " at [Source: (String)\"\"; line: 1, column: 0]"));
    }

    @Test
    public void shouldPopulateBidAdmIfResponseAssetsIsNull() throws JsonProcessingException {
        // given
        final String adm = mapper.writeValueAsString(Response.builder()
                .assets(null)
                .dcourl("test-field")
                .build());

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid("impId1")
                .price(BigDecimal.valueOf(5.67))
                .nurl(BID_NURL)
                .adm(adm)
                .build();

        final String bidder1 = "bidder1";
        final List<BidderResponse> bidderResponses = List.of(BidderResponse.of(bidder1,
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(Imp.builder()
                        .id("impId1")
                        .xNative(Native.builder().build())
                        .build()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse)
                .extracting(BidResponse::getSeatbid)
                .extracting(seatBids -> seatBids.get(0))
                .extracting(SeatBid::getBid)
                .extracting(bids -> bids.get(0))
                .extracting(Bid::getAdm)
                .isEqualTo(adm);
    }

    @Test
    public void shouldPopulateEventsContextForRequestIfEventsEnabledForRequest() {
        // given
        final AccountAuctionEventConfig accountAuctionEventConfig = AccountAuctionEventConfig.builder().build();
        accountAuctionEventConfig.addEvent("pbjs", true);

        final Account account = Account.builder()
                .id("accountId")
                .analytics(AccountAnalyticsConfig.of(accountAuctionEventConfig, emptyMap()))
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(bidRequestBuilder ->
                                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .channel(ExtRequestPrebidChannel.of("pbjs"))
                                        .build())),
                        givenImp()),
                contextBuilder -> contextBuilder
                        .account(account)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        final Events givenEvents = Events.of(
                "http://win-url?auctionId=123&timestamp=1000",
                "http://imp-url?auctionId=123&timestamp=1000");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
                .willReturn(givenEvents);

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getEvents())
                .containsOnly(givenEvents);
    }

    @Test
    public void shouldNotPopulateBidExtTargetingWhenExtRequestTargetingPricegranularityIsNull() {
        // given
        final ExtRequestTargeting extRequestTargeting = ExtRequestTargeting.builder()
                .pricegranularity(null)
                .includewinners(true)
                .build();

        final Bid bidder1Bid1 = Bid.builder().id("bidder1Bid1").price(BigDecimal.valueOf(3.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bidder1Bid1, banner, null)), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(extRequestTargeting),
                        givenImp("i1")),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        final ObjectNode givenDefaultBidExt =
                mapper.createObjectNode().set("prebid", mapper.createObjectNode().put("type", "banner"));
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(givenDefaultBidExt);
    }

    @Test
    public void shouldCopyRequestExtPrebidPassThroughToResponseExtPrebidPassThroughWhenPresent() {
        // given
        final Bid bid = Bid.builder().id("bidder1Bid1").price(BigDecimal.valueOf(3.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, null)), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.passthrough(TextNode.valueOf("passthrough")),
                        givenImp("i1")),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getPrebid)
                .extracting(ExtBidResponsePrebid::getPassthrough)
                .isEqualTo(TextNode.valueOf("passthrough"));
    }

    @Test
    public void shouldCopyImpExtPrebidPassThroughToResponseBidExtPrebidPassThroughWhenPresentInCorrespondingImp() {
        // given
        final ExtImp impExt = ExtImp.of(
                ExtImpPrebid.builder()
                        .passthrough(TextNode.valueOf("passthrough"))
                        .build(),
                null);
        final Imp imp = givenImp("i1").toBuilder().ext(mapper.valueToTree(impExt)).build();

        final Bid bid = Bid.builder().id("bidder1Bid1").price(BigDecimal.valueOf(3.67)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, null)), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.passthrough(TextNode.valueOf("passthrough")),
                        imp),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.at("/prebid/passthrough"))
                .containsExactly(TextNode.valueOf("passthrough"));
    }

    @Test
    public void shouldAddExtPrebidFledgeIfAvailable() {
        // given
        final Imp imp = givenImp("i1").toBuilder()
                .ext(mapper.createObjectNode().put("ae", 1))
                .build();
        final BidRequest bidRequest = givenBidRequest(identity(), identity(), imp);
        final FledgeAuctionConfig fledgeAuctionConfig = givenFledgeAuctionConfig("i1");
        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(2.37)).impid("i1").build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1",
                        BidderSeatBid.builder()
                                .bids(List.of(BidderBid.of(bid, banner, "USD")))
                                .fledgeAuctionConfigs(List.of(fledgeAuctionConfig))
                                .build(), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator
                .create(auctionContext, CACHE_INFO, MULTI_BIDS)
                .result();

        // then
        assertThat(bidResponse.getExt().getPrebid().getFledge().getAuctionConfigs())
                .isNotEmpty()
                .first()
                .usingRecursiveComparison()
                .isEqualTo(fledgeAuctionConfig.toBuilder()
                        .bidder("bidder1")
                        .adapter("bidder1")
                        .build());
    }

    @Test
    public void shouldAddExtPrebidFledgeIfAvailableEvenIfBidsEmpty() {
        // given
        final Imp imp = givenImp("i1").toBuilder()
                .ext(mapper.createObjectNode().put("ae", 1))
                .build();
        final BidRequest bidRequest = givenBidRequest(identity(), identity(), imp);
        final FledgeAuctionConfig fledgeAuctionConfig = givenFledgeAuctionConfig("i1");
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1",
                        BidderSeatBid.builder()
                                .bids(Collections.emptyList())
                                .fledgeAuctionConfigs(List.of(fledgeAuctionConfig))
                                .build(), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator
                .create(auctionContext, CACHE_INFO, MULTI_BIDS)
                .result();

        // then
        assertThat(bidResponse.getExt().getPrebid().getFledge().getAuctionConfigs())
                .isNotEmpty()
                .first()
                .usingRecursiveComparison()
                .isEqualTo(fledgeAuctionConfig.toBuilder()
                        .bidder("bidder1")
                        .adapter("bidder1")
                        .build());
    }

    @Test
    public void shouldDropFledgeResponsesReferencingUnknownImps() {
        // given
        final Imp imp = givenImp("i1");
        final BidRequest bidRequest = givenBidRequest(identity(), identity(), imp);
        final FledgeAuctionConfig fledgeAuctionConfig = givenFledgeAuctionConfig("i1");
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1",
                        BidderSeatBid.builder()
                                .bids(Collections.emptyList())
                                .fledgeAuctionConfigs(List.of(fledgeAuctionConfig))
                                .build(), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator
                .create(auctionContext, CACHE_INFO, MULTI_BIDS)
                .result();

        // then
        assertThat(bidResponse.getExt().getPrebid().getFledge())
                .isNull();
    }

    @Test
    public void shouldPopulateExtPrebidSeatNonBidWhenReturnAllBidStatusFlagIsTrue() {
        // given
        final BidRejectionTracker bidRejectionTracker = mock(BidRejectionTracker.class);
        given(bidRejectionTracker.getRejectionReasons()).willReturn(singletonMap("impId2", BidRejectionReason.NO_BID));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(3.67)).impid("impId").build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(
                        "someBidder",
                        givenSeatBid(BidderBid.of(bid, banner, null)),
                        100));

        final List<AuctionParticipation> auctionParticipations = toAuctionParticipant(bidderResponses);

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp("impId")),
                contextBuilder -> contextBuilder
                        .auctionParticipations(auctionParticipations)
                        .bidRejectionTrackers(singletonMap("someBidder", bidRejectionTracker))
                        .debugContext(DebugContext.of(false, true, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator
                .create(auctionContext, CACHE_INFO, MULTI_BIDS)
                .result();

        // then
        final SeatNonBid expectedSeatNonBid = SeatNonBid.of(
                "someBidder", singletonList(NonBid.of("impId2", BidRejectionReason.NO_BID)));

        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getSeatnonbid)
                .asList()
                .containsExactly(expectedSeatNonBid);
    }

    @Test
    public void shouldNotPopulateExtPrebidSeatNonBidWhenReturnAllBidStatusFlagIsFalse() {
        // given
        final BidRejectionTracker bidRejectionTracker = mock(BidRejectionTracker.class);
        given(bidRejectionTracker.getRejectionReasons()).willReturn(singletonMap("impId2", BidRejectionReason.NO_BID));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(3.67)).impid("impId").build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(
                        "someBidder",
                        givenSeatBid(BidderBid.of(bid, banner, null)),
                        100));
        final List<AuctionParticipation> auctionParticipations = toAuctionParticipant(bidderResponses);

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp("impId")),
                contextBuilder -> contextBuilder
                        .auctionParticipations(auctionParticipations)
                        .bidRejectionTrackers(singletonMap("someBidder", bidRejectionTracker))
                        .debugContext(DebugContext.of(false, false, null)));

        // when
        final BidResponse bidResponse = bidResponseCreator
                .create(auctionContext, CACHE_INFO, MULTI_BIDS)
                .result();

        // then
        assertThat(bidResponse.getExt())
                .extracting(ExtBidResponse::getSeatnonbid)
                .isNull();
    }

    @Test
    public void shouldPopulateBidExtWhenExtMediaTypePriceGranularityHasValidVideoExtPriceGranularity() {
        // given
        final ExtMediaTypePriceGranularity extMediaTypePriceGranularity = ExtMediaTypePriceGranularity.of(
                null,
                mapper.valueToTree(ExtPriceGranularity.of(
                        3,
                        singletonList(ExtGranularityRange.of(
                                BigDecimal.valueOf(10), BigDecimal.valueOf(1))))),
                null);

        final ExtPriceGranularity extPriceGranularity = ExtPriceGranularity.of(2,
                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))));

        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(extPriceGranularity))
                .mediatypepricegranularity(extMediaTypePriceGranularity)
                .includebidderkeys(true)
                .includewinners(true)
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(targeting),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder_bidder1", "bidder1"));
    }

    @Test
    public void shouldPopulateBidExtWhenExtMediaTypePriceGranularityHasValidxNativeExtPriceGranularity() {
        // given
        final ExtMediaTypePriceGranularity extMediaTypePriceGranularity = ExtMediaTypePriceGranularity.of(
                null, null,
                mapper.valueToTree(ExtPriceGranularity.of(
                        3,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(1))))));

        final ExtPriceGranularity extPriceGranularity = ExtPriceGranularity.of(2,
                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))));

        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(extPriceGranularity))
                .mediatypepricegranularity(extMediaTypePriceGranularity)
                .includewinners(true)
                .includebidderkeys(true)
                .build();

        final TxnLog txnLog = TxnLog.create();
        txnLog.lineItemsSentToBidder();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                        identity(), extBuilder -> extBuilder.targeting(targeting), givenImp()),
                auctionContextBuilder -> auctionContextBuilder
                        .txnLog(txnLog)
                        .auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder_bidder1", "bidder1"));
    }

    @Test
    public void shouldThrowErrorIfExtMediaTypePriceGranularityCannotBeParsed() {
        // given
        final ExtMediaTypePriceGranularity extMediaTypePriceGranularity = ExtMediaTypePriceGranularity.of(
                null,
                null,
                mapper.createObjectNode().put("precision", "2").put("ranges", "2"));

        final ExtPriceGranularity extPriceGranularity = ExtPriceGranularity.of(2,
                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))));

        final ExtRequestTargeting targeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(extPriceGranularity))
                .mediatypepricegranularity(extMediaTypePriceGranularity)
                .includewinners(true)
                .includebidderkeys(true)
                .includeformat(false)
                .build();

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.targeting(targeting),
                        givenImp()),
                contextBuilder -> contextBuilder.auctionParticipations(toAuctionParticipant(bidderResponses)));

        // when
        final Future<BidResponse> bidResponse = bidResponseCreator.create(auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        assertThat(bidResponse.failed()).isTrue();
        assertThat(bidResponse.cause())
                .hasMessageStartingWith("Error decoding bidRequest.prebid.targeting.pricegranularity: "
                        + "Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request"
                        + ".ExtGranularityRange");
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest,
                                               UnaryOperator<AuctionContext.AuctionContextBuilder> contextCustomizer) {

        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .account(Account.empty("accountId"))
                .bidRequest(bidRequest)
                .txnLog(TxnLog.create())
                .timeout(timeout)
                .debugContext(DebugContext.empty())
                .deepDebugLog(DeepDebugLog.create(false, clock))
                .debugHttpCalls(new HashMap<>())
                .debugWarnings(emptyList())
                .auctionParticipations(emptyList())
                .bidRejectionTrackers(new HashMap<>())
                .prebidErrors(new ArrayList<>());

        return contextCustomizer.apply(auctionContextBuilder).build();
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return givenAuctionContext(bidRequest, identity());
    }

    private static List<AuctionParticipation> toAuctionParticipant(List<BidderResponse> bidderResponses) {
        return bidderResponses.stream()
                .map(bidderResponse -> AuctionParticipation.builder()
                        .bidder(bidderResponse.getBidder())
                        .bidderResponse(bidderResponse)
                        .build())
                .toList();
    }

    private void givenCacheServiceResult(List<CacheInfo> cacheInfos) {
        given(cacheService.cacheBidsOpenrtb(anyList(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(CacheServiceResult.of(
                        null,
                        null,
                        zipBidsWithCacheInfos(invocation.getArgument(0), cacheInfos))));
    }

    private void givenCacheServiceResult(CacheServiceResult cacheServiceResult) {
        given(cacheService.cacheBidsOpenrtb(anyList(), any(), any(), any()))
                .willReturn(Future.succeededFuture(cacheServiceResult));
    }

    private static Map<Bid, CacheInfo> zipBidsWithCacheInfos(List<BidInfo> bidInfos, List<CacheInfo> cacheInfos) {
        return IntStream.range(0, Math.min(bidInfos.size(), cacheInfos.size()))
                .boxed()
                .collect(Collectors.toMap(i -> bidInfos.get(i).getBid(), cacheInfos::get));
    }

    private static BidInfo toBidInfo(Bid bid,
                                     Imp correspondingImp,
                                     String bidder,
                                     BidType bidType,
                                     boolean isWinningBid) {

        return BidInfo.builder()
                .bid(bid)
                .correspondingImp(correspondingImp)
                .bidder(bidder)
                .bidType(bidType)
                .targetingInfo(TargetingInfo.builder()
                        .bidderCode(bidder)
                        .isTargetingEnabled(true)
                        .isWinningBid(isWinningBid)
                        .isBidderWinningBid(true)
                        .isAddTargetBidderCode(false)
                        .build())
                .build();
    }

    private static Imp givenImp() {
        return givenImp(IMP_ID);
    }

    private static Imp givenImp(String impId) {
        return Imp.builder().id(impId).build();
    }

    private static Imp givenImp(Map<String, String> dealIdToLineItemId) {
        Pmp pmp = null;
        if (MapUtils.isNotEmpty(dealIdToLineItemId)) {
            final List<Deal> deals = dealIdToLineItemId.entrySet().stream()
                    .map(dealIdAndLineId -> Deal.builder()
                            .id(dealIdAndLineId.getKey())
                            .ext(mapper.valueToTree(ExtDeal.of(ExtDealLine.of(
                                    dealIdAndLineId.getValue(), null, null, null)))).build())
                    .toList();
            pmp = Pmp.builder().deals(deals).build();
        }

        return Imp.builder().id(IMP_ID).pmp(pmp).build();
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
        return BidderSeatBid.of(List.of(bids));
    }

    private static FledgeAuctionConfig givenFledgeAuctionConfig(String impId) {
        return FledgeAuctionConfig.builder()
                .impId(impId)
                .config(mapper.createObjectNode().put("references", impId))
                .build();
    }

    private static ExtRequestTargeting givenTargeting() {
        return ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(
                        ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                BigDecimal.valueOf(0.5))))))
                .includewinners(true)
                .includebidderkeys(true)
                .includeformat(false)
                .build();
    }

    private static ExtBidPrebid toExtBidPrebid(ObjectNode ext) {
        try {
            return mapper.treeToValue(ext.get("prebid"), ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return rethrow(e);
        }
    }

    private BidResponseCreator givenBidResponseCreator(int truncateAttrChars) {
        return new BidResponseCreator(
                cacheService,
                bidderCatalog,
                vastModifier,
                eventsService,
                storedRequestProcessor,
                winningBidComparatorFactory,
                idGenerator,
                hookStageExecutor,
                categoryMappingService,
                truncateAttrChars,
                clock,
                jacksonMapper);
    }

    private static String toTargetingByKey(Bid bid, String targetingKey) {
        final Map<String, String> targeting = toExtBidPrebid(bid.getExt()).getTargeting();
        return targeting != null ? targeting.get(targetingKey) : null;
    }

    private static String getTargetingBidderCode(Bid bid) {
        return toExtBidPrebid(bid.getExt()).getTargetBidderCode();
    }

    private static ObjectNode extWithTargeting(String targetBidderCode, Map<String, String> targeting) {
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder()
                .type(banner)
                .targeting(targeting)
                .targetBidderCode(targetBidderCode)
                .build();

        final ObjectNode ext = mapper.createObjectNode();
        ext.set("prebid", mapper.valueToTree(extBidPrebid));
        return ext;
    }

    @SafeVarargs
    private static <T> List<T> mutableList(T... values) {
        return Arrays.stream(values).collect(Collectors.toCollection(ArrayList::new));
    }

    @Accessors(fluent = true)
    @Value(staticConstructor = "of")
    private static class BidderResponsePayloadImpl implements BidderResponsePayload {

        List<BidderBid> bids;
    }
}
