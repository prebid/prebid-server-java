package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import lombok.Value;
import lombok.experimental.Accessors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.TargetingInfo;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.identity.IdGeneratorType;
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
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.settings.model.VideoStoredDataResult;
import org.prebid.server.vast.VastModifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source.xStatic;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

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

        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any()))
                .willReturn(Future.succeededFuture(VideoStoredDataResult.empty()));
        given(idGenerator.getType()).willReturn(IdGeneratorType.none);

        given(hookStageExecutor.executeProcessedBidderResponseStage(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        BidderResponsePayloadImpl.of(((BidderResponse) invocation.getArgument(0))
                                .getSeatBid()
                                .getBids()))));

        clock = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);

        bidResponseCreator = new BidResponseCreator(
                cacheService,
                bidderCatalog,
                vastModifier,
                eventsService,
                storedRequestProcessor,
                winningBidComparatorFactory,
                idGenerator,
                hookStageExecutor,
                0,
                clock,
                jacksonMapper);

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);
    }

    @Test
    public void shouldPassBidWithGeneratedIdAndPreserveExtFieldsWhenIdGeneratorTypeUuid() {
        // given
        final Imp imp = givenImp();
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(imp));
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

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

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

        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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

        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getId, Bid::getImpid, Bid::getPrice)
                .containsOnly(tuple("bidIdModifiedByHook", IMP_ID, BigDecimal.valueOf(1.23)));
    }

    @Test
    public void shouldPassOriginalTimeoutToCacheServiceIfCachingIsRequested() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.valueOf(5.67))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

        // then
        verify(cacheService).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldRequestCacheServiceWithExpectedArguments() {
        // given
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
                        .build()));

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

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

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
        final Imp imp1 = givenImp("impId1");
        final Imp imp2 = givenImp("impId2");
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                imp1, imp2, givenImp("impId3"), givenImp("impId4")));

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

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

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

        final Imp imp1 = givenImp("impId1");
        final Imp imp2 = givenImp("impId2");
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.events(mapper.createObjectNode()),
                        imp1, imp2),
                contextBuilder -> contextBuilder.account(account));

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

        final String modifiedAdm = "modifiedAdm";
        given(vastModifier.createBidVastXml(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(modifiedAdm);

        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

        // then
        final EventsContext expectedEventContext = EventsContext.builder()
                .auctionId("123")
                .enabledForAccount(true)
                .enabledForRequest(true)
                .auctionTimestamp(1000L)
                .build();

        verify(vastModifier)
                .createBidVastXml(eq(bidder1), eq(null), eq(BID_NURL), eq(bidId1),
                        eq(accountId), eq(expectedEventContext), any());

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

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

        final String modifiedVast = "modifiedVast";
        given(vastModifier
                .createBidVastXml(anyString(), anyString(), anyString(),
                        anyString(), anyString(), any(), any()))
                .willReturn(modifiedVast);

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsOnly(modifiedVast);

        verify(vastModifier)
                .createBidVastXml(eq(bidder), eq(BID_ADM), eq(BID_NURL), eq(bidId), eq("accountId"), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldCallCacheServiceEvenRoundedCpmIsZero() {
        // given
        final Imp imp1 = givenImp();
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(imp1));

        final Bid bid1 = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(0.05)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        // just a stub to get through method call chain
        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, null, MULTI_BIDS).result();

        // then
        final BidResponse responseWithExpectedFields = BidResponse.builder()
                .id("123")
                .cur("USD")
                .ext(ExtBidResponse.builder()
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.of(1000L, null))
                        .build())
                .build();

        assertThat(bidResponse)
                .isEqualToIgnoringGivenFields(responseWithExpectedFields, "nbr", "seatbid");

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesAreEmpty() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(emptyList(), auctionContext, null, MULTI_BIDS).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesDoNotContainAnyBids() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, null, MULTI_BIDS).result();

        // then
        assertThat(bidResponse).returns(0, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSetNbrNullAndPopulateSeatbidWhenAtLeastOneBidIsPresent() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final Bid bid = Bid.builder().impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, null)), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getNbr()).isNull();
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldSkipBidderResponsesWhereSeatBidContainEmptyBids() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final Bid bid = Bid.builder().impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(), 0),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldOverrideBidIdWhenIdGeneratorIsUUID() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));
        final Bid bid = Bid.builder()
                .id("123")
                .impid(IMP_ID)
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.valueToTree(ExtBidPrebid.builder().type(banner).build())))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        given(idGenerator.getType()).willReturn(IdGeneratorType.uuid);
        given(idGenerator.generateId()).willReturn("de7fc739-0a6e-41ad-8961-701c30c82166");

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .impid(IMP_ID)
                .price(BigDecimal.ONE)
                .build();

        final String bidder = "bidder1";
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of(bidder, givenSeatBid(BidderBid.of(bid, banner, "USD")), 0));

        final String generatedBidId = "de7fc739-0a6e-41ad-8961-701c30c82166";
        given(idGenerator.getType()).willReturn(IdGeneratorType.uuid);
        given(idGenerator.generateId()).willReturn(generatedBidId);

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, null, null)));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().bidid(generatedBidId).type(banner).build();
        final Bid expectedBid = bid.toBuilder()
                .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(extBidPrebid)))
                .build();

        final BidInfo expectedBidInfo = toBidInfo(expectedBid, imp, bidder, banner, true);
        verify(cacheService).cacheBidsOpenrtb(eq(singletonList(expectedBidInfo)), any(), any(), any());

        verify(eventsService).createEvent(eq(generatedBidId), anyString(), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldSetExpectedResponseSeatBidAndBidFields() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));
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

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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

    @SuppressWarnings("unchecked")
    @Test
    public void shouldNotWriteSkadnAttributeToBidderSection() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));
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

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

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

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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

        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

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

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
                        .id(IMP_ID)
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

        final Bid bid = Bid.builder().id("bidId")
                .price(BigDecimal.ONE)
                .impid(IMP_ID)
                .adm(mapper.writeValueAsString(responseAdm))
                .ext(mapper.valueToTree(singletonMap("bidExt", 1)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, xNative, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder()
                .price(BigDecimal.ONE)
                .adm(BID_ADM)
                .id("bidId")
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, null, null)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().price(BigDecimal.ONE).adm(BID_ADM).id("bidId").impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().price(BigDecimal.ONE).impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, 100, null)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().price(BigDecimal.ONE).impid(IMP_ID).id("bidId").build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        givenCacheServiceResult(singletonList(CacheInfo.of("id", null, 100, 200)));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.ONE).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(givenImp("i1"))),
                extBuilder -> extBuilder.targeting(givenTargeting().toBuilder().preferdeals(true).build())));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        verify(winningBidComparatorFactory, times(2)).create(eq(true));
    }

    @Test
    public void shouldPassPreferDealsFalseWhenBidRequestPreferDealsIsNotDefined() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(givenImp("i1"))),
                extBuilder -> extBuilder.targeting(givenTargeting())));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid("i1").build();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        verify(winningBidComparatorFactory, times(2)).create(eq(false));
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidResponseCreator bidResponseCreator = new BidResponseCreator(
                cacheService,
                bidderCatalog,
                vastModifier,
                eventsService,
                storedRequestProcessor,
                winningBidComparatorFactory,
                idGenerator,
                hookStageExecutor,
                20,
                clock,
                jacksonMapper);

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("someVeryLongBidderName",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp("i1"), givenImp("i2")));

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

        // when
        final BidResponse result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, multiBidMap).result();

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
    public void shouldReduceAndPopulateTargetingKeywordsForExtraBidsWhenCodePrefixIsDefined() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp("i1"), givenImp("i2")));

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

        // when
        final BidResponse result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, multiBidMap).result();

        // then
        final Map<String, String> bidder1Bid4Targeting = new HashMap<>();
        bidder1Bid4Targeting.put("hb_pb", "5.00");
        bidder1Bid4Targeting.put("hb_pb_" + bidder1, "5.00");
        bidder1Bid4Targeting.put("hb_bidder_" + bidder1, bidder1);
        bidder1Bid4Targeting.put("hb_bidder", bidder1);
        final ObjectNode bidder1Bid4Ext = extWithTargeting(bidder1, bidder1Bid4Targeting);
        final Bid expectedBidder1Bid4 = bidder1Bid4.toBuilder().ext(bidder1Bid4Ext).build();

        final String bidderCodeForBidder1Bid2 = String.format("%s%s", codePrefix, 2);
        final Map<String, String> bidder1Bid2Targeting = new HashMap<>();
        bidder1Bid2Targeting.put("hb_bidder_" + bidderCodeForBidder1Bid2, bidderCodeForBidder1Bid2);
        bidder1Bid2Targeting.put("hb_pb_" + bidderCodeForBidder1Bid2, "4.50");
        final ObjectNode bidder1Bid2Ext = extWithTargeting(bidderCodeForBidder1Bid2, bidder1Bid2Targeting);
        final Bid expectedBidder1Bid2 = bidder1Bid2.toBuilder().ext(bidder1Bid2Ext).build();

        final String bidderCodeForBidder1Bid1 = String.format("%s%s", codePrefix, 3);
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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp("i1"), givenImp("i2")));

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

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(targeting),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .targeting(givenTargeting())
                        .adservertargeting(singletonList(ExtRequestPrebidAdservertargetingRule.of(
                                "static_keyword1", xStatic, "static_keyword1"))),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of(
                "bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).adm(BID_ADM).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .returnCreativeBids(false) // this will cause erasing of bid.adm
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", null, null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

        // Check if you didn't lost any bids because of bid change in winningBids set
        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(extractedBid -> toExtBidPrebid(extractedBid.getExt()).getTargeting())
                .doesNotContainNull();
    }

    @SuppressWarnings("unchecked")
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

        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any()))
                .willReturn(events);

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .analytics(AccountAnalyticsConfig.of(singletonMap("web", true), null))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder
                        .channel(ExtRequestPrebidChannel.of("web"))
                        .integration("pbjs"),
                givenImp());
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any()))
                .willReturn(events);

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final Events events = Events.of("http://event-type-win", "http://event-type-view");
        given(eventsService.createEvent(anyString(), anyString(), anyString(), any()))
                .willReturn(events);

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsOnly(events);

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldAddExtPrebidVideo() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp()));

        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(1, "category")).build();
        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .ext(mapper.createObjectNode().set("prebid", mapper.valueToTree(extBidPrebid)))
                .build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
    public void shouldNotAddExtPrebidEventsIfEventsAreNotEnabled() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(false))
                        .build())
                .build();
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(
                        identity(),
                        extBuilder -> extBuilder.events(mapper.createObjectNode()),
                        givenImp()),
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsNull();
    }

    @Test
    public void shouldNotAddExtPrebidEventsIfAccountDoesNotSupportEventsForChannel() {
        // given
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .analytics(AccountAnalyticsConfig.of(singletonMap("web", true), null))
                .build();
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.channel(ExtRequestPrebidChannel.of("amp")),
                givenImp());
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder()
                .id("bidId1")
                .price(BigDecimal.valueOf(5.67))
                .impid(IMP_ID)
                .build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(responseBid -> toExtBidPrebid(responseBid.getExt()).getEvents())
                .containsNull();
    }

    @Test
    public void shouldReturnCacheEntityInExt() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))))
                        .includewinners(false)
                        .includebidderkeys(true)
                        .includeformat(false)
                        .build()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(
                                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                        BigDecimal.valueOf(0.5))))))
                        .includewinners(true)
                        .includebidderkeys(false)
                        .includeformat(false)
                        .build()),
                givenImp()));

        final Bid bid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).impid(IMP_ID).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId", "videoId", null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                identity(),
                extBuilder -> extBuilder.targeting(givenTargeting()),
                givenImp("impId1"), givenImp("impId2")));

        final Bid firstBid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid secondBid = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(firstBid, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(secondBid, banner, null)), 123));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId2", null, null, null)));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final Imp imp2 = givenImp("impId2");
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(givenImp("impId1"), imp2));

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.ZERO).dealid("dealId2").build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid2, banner, null)), 99));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(singletonList(CacheInfo.of("cacheId2", null, null, null)));

        // when
        bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

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
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).adm("[]").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, xNative, null)), null,
                        singletonList(BidderError.badInput("bad_input"))), 100));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(CacheServiceResult.of(
                DebugHttpCall.builder().endpoint("http://cache-service/cache").responseTimeMillis(666).build(),
                new RuntimeException("cacheError"), emptyMap()));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ExtBidResponse responseExt = bidResponse.getExt();

        assertThat(responseExt.getDebug()).isNull();
        assertThat(responseExt.getWarnings()).isNull();
        assertThat(responseExt.getUsersync()).isNull();
        assertThat(responseExt.getTmaxrequest()).isEqualTo(1000L);

        assertThat(responseExt.getErrors()).hasSize(2).containsOnly(
                entry("bidder1", asList(
                        ExtBidderError.of(2, "bad_input"),
                        ExtBidderError.of(3, "Failed to decode: Cannot deserialize instance of `com.iab."
                                + "openrtb.response.Response` out of START_ARRAY token\n at [Source: (String)\"[]\"; "
                                + "line: 1, column: 1]"))),
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
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(imp));

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any())).willReturn(
                Future.failedFuture("Fetch failed"));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS);

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
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(2)).build();
        final Bid bid3 = Bid.builder().id("bidId3").impid("impId3").price(BigDecimal.valueOf(3)).build();
        final List<BidderBid> bidderBids = mutableList(
                BidderBid.of(bid1, banner, "USD"),
                BidderBid.of(bid2, banner, "USD"),
                BidderBid.of(bid3, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        final Video storedVideo = Video.builder().maxduration(100).h(2).w(2).build();
        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any()))
                .willReturn(Future.succeededFuture(
                        VideoStoredDataResult.of(singletonMap("impId1", storedVideo), emptyList())));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();
        givenCacheServiceResult(emptyList());

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

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
        final AuctionContext auctionContext = givenAuctionContext(bidRequest);

        final Bid bid1 = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        given(storedRequestProcessor.videoStoredDataResult(any(), anyList(), anyList(), any()))
                .willReturn(Future.failedFuture("Bad timeout"));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        verify(storedRequestProcessor).videoStoredDataResult(any(), eq(singletonList(imp1)), anyList(), eq(timeout));

        assertThat(result.result().getExt()).isEqualTo(
                ExtBidResponse.builder()
                        .errors(singletonMap("prebid",
                                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(), "Bad timeout"))))
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.of(1000L, null))
                        .build());
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
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS).result();

        // then
        assertThat(bidResponse.getExt()).isEqualTo(
                ExtBidResponse.builder()
                        .errors(singletonMap(invalidBidderName,
                                singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                        "invalid has been deprecated and is no longer available. Use valid instead."))))
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.of(1000L, null))
                        .build());

        verify(cacheService, never()).cacheBidsOpenrtb(anyList(), any(), any(), any());
    }

    @Test
    public void shouldProcessRequestAndAddErrorFromAuctionContext() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                givenBidRequest(givenImp()),
                contextBuilder -> contextBuilder.prebidErrors(singletonList("privacy error")));

        final Bid bid1 = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderBid> bidderBids = singletonList(BidderBid.of(bid1, banner, "USD"));
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", BidderSeatBid.of(bidderBids, emptyList(), emptyList()), 100));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, CACHE_INFO, MULTI_BIDS);

        // then
        assertThat(result.result().getExt()).isEqualTo(
                ExtBidResponse.builder()
                        .errors(singletonMap(
                                "prebid",
                                singletonList(ExtBidderError.of(BidderError.Type.generic.getCode(), "privacy error"))))
                        .responsetimemillis(singletonMap("bidder1", 100))
                        .tmaxrequest(1000L)
                        .prebid(ExtBidResponsePrebid.of(1000L, null))
                        .build());
    }

    @Test
    public void shouldPopulateResponseDebugExtensionAndWarningsIfDebugIsEnabled() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());
        final List<String> warnings = asList("warning1", "warning2");
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                builder -> builder
                        .debugWarnings(warnings)
                        .debugContext(DebugContext.of(true, null)));
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
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(singletonList(BidderBid.of(bid, banner, null)),
                        singletonList(ExtHttpCall.builder().status(200).build()), null), 100));

        // when
        final BidResponse bidResponse =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS).result();

        // then
        final ExtBidResponse responseExt = bidResponse.getExt();

        assertThat(responseExt.getDebug()).isNotNull();
        assertThat(responseExt.getDebug().getHttpcalls()).hasSize(2)
                .containsOnly(
                        entry("bidder1", singletonList(ExtHttpCall.builder().status(200).build())),
                        entry("cache", singletonList(ExtHttpCall.builder().uri("test.uri").status(500).build())));

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
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequest,
                contextBuilder -> contextBuilder.account(account));

        final Bid bid = Bid.builder().id("bidId1").impid(IMP_ID).price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().doCaching(true).build();

        givenCacheServiceResult(singletonList(CacheInfo.empty()));

        given(eventsService.createEvent(anyString(), any(), anyString(), any()))
                .willReturn(Events.of(
                        "http://win-url?param=value&int=integration",
                        "http://imp-url?param=value&int=integration"));

        // when
        final Future<BidResponse> result =
                bidResponseCreator.create(bidderResponses, auctionContext, cacheInfo, MULTI_BIDS);

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

    private AuctionContext givenAuctionContext(BidRequest bidRequest,
                                               UnaryOperator<AuctionContext.AuctionContextBuilder> contextCustomizer) {

        final AuctionContext.AuctionContextBuilder auctionContextBuilder = AuctionContext.builder()
                .account(Account.empty("accountId"))
                .bidRequest(bidRequest)
                .timeout(timeout)
                .prebidErrors(emptyList())
                .debugContext(DebugContext.empty());

        return contextCustomizer.apply(auctionContextBuilder).build();
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return givenAuctionContext(bidRequest, identity());
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
        return BidderSeatBid.of(mutableList(bids), emptyList(), emptyList());
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
        return Arrays.stream(values).collect(Collectors.toList());
    }

    @Accessors(fluent = true)
    @Value(staticConstructor = "of")
    private static class BidderResponsePayloadImpl implements BidderResponsePayload {

        List<BidderBid> bids;
    }
}
