package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.vast.VastModifier;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class CacheServiceTest extends VertxTest {

    public static final String ACCOUNT_ID = "accountId";
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final CacheTtl mediaTypeCacheTtl = CacheTtl.of(null, null);

    @Mock
    private HttpClient httpClient;
    @Mock
    private EventsService eventsService;
    @Mock
    private VastModifier vastModifier;
    @Mock
    private Metrics metrics;

    private Clock clock;

    private CacheService cacheService;

    private EventsContext eventsContext;

    private Timeout timeout;

    private Timeout expiredTimeout;

    @Before
    public void setUp() throws MalformedURLException, JsonProcessingException {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        cacheService = new CacheService(
                mediaTypeCacheTtl,
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                vastModifier,
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        eventsContext = EventsContext.builder().build();

        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);

        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        given(httpClient.post(anyString(), any(), any(), anyLong())).willReturn(Future.succeededFuture(
                HttpClientResponse.of(200, null, mapper.writeValueAsString(
                        BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))))));
    }

    @Test
    public void getCacheEndpointUrlShouldFailOnInvalidCacheServiceUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCacheEndpointUrl("http", "{invalid:host}", "cache"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCacheEndpointUrl("invalid-schema", "example-server:80808", "cache"));
    }

    @Test
    public void getCacheEndpointUrlShouldReturnValidUrl() {
        // when
        final String result = CacheService.getCacheEndpointUrl("http", "example.com", "cache").toString();

        // then
        assertThat(result).isEqualTo("http://example.com/cache");
    }

    @Test
    public void getCachedAssetUrlTemplateShouldFailOnInvalidCacheServiceUrl() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCachedAssetUrlTemplate("http", "{invalid:host}", "cache", "qs"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCachedAssetUrlTemplate("invalid-schema", "example-server:80808", "cache", "qs"));
    }

    @Test
    public void getCachedAssetUrlTemplateShouldReturnValidUrl() {
        // when
        final String result = CacheService.getCachedAssetUrlTemplate("http", "example.com", "cache", "qs");

        // then
        assertThat(result).isEqualTo("http://example.com/cache?qs");
    }

    @Test
    public void getCachedAssetURLShouldReturnExpectedValue() {
        // when
        final String cachedAssetURL = cacheService.getCachedAssetURLTemplate();

        // then
        assertThat(cachedAssetURL).isEqualTo("http://cache-service-host/cache?uuid=");
    }

    @Test
    public void cacheBidsOpenrtbShouldNeverCallCacheServiceIfNoBidsPassed() {
        // when
        cacheService.cacheBidsOpenrtb(emptyList(), null, null, null);

        // then
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        verify(httpClient).post(anyString(), any(), any(), eq(500L));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(identity())),
                givenAuctionContext().toBuilder().timeout(expiredTimeout).build(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isNotNull().hasMessage("Timeout has been exceeded");
        assertThat(result.getHttpCall()).isNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldStoreWinUrl() {
        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(builder -> builder.id("bidId1"))),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                EventsContext.builder().enabledForAccount(true).enabledForRequest(true).build());

        // then
        verify(eventsService).winUrl(eq("bidId1"), eq("bidder"), eq(ACCOUNT_ID), isNull(), isNull());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateReadingHttpResponseFails() throws JsonProcessingException {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        verify(metrics).updateCacheRequestFailedTime(eq(ACCOUNT_ID), anyLong());

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestBody(request.getBody())
                        .requestUri(request.getUri())
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseCodeIsNot200() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(503, "response");
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("HTTP status code 503");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestBody(request.getBody())
                        .requestUri(request.getUri())
                        .responseStatus(503)
                        .responseBody("response")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseBodyCouldNotBeParsed() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "response");
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("Cannot parse response: response");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestUri(request.getUri())
                        .requestBody(request.getBody())
                        .responseStatus(200)
                        .responseBody("response")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateCacheEntriesNumberDoesNotMatchBidsNumber()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "{}");
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class)
                .hasMessage("The number of response cache objects doesn't match with bids");

        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestBody(request.getBody())
                        .requestUri(request.getUri())
                        .responseStatus(200).responseBody("{}")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedDebugInfo() throws JsonProcessingException {
        // given
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bidinfo.getBid());
        assertThat(result.getHttpCall())
                .isEqualTo(DebugHttpCall.builder()
                        .endpoint("http://cache-service/cache")
                        .requestUri(request.getUri())
                        .requestBody(request.getBody())
                        .responseStatus(200)
                        .responseBody("{\"responses\":[{\"uuid\":\"uuid1\"}]}")
                        .responseTimeMillis(0)
                        .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedCacheBids() {
        // given
        final BidInfo bidinfo = givenBidInfo(builder -> builder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidinfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).hasSize(1)
                .containsEntry(bidinfo.getBid(), CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        final ObjectNode bidExt2 = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().bidid("generatedId").build(),
                        emptyMap()));
        final String receivedBid2Adm = "adm2";
        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.banner, "bidder1");
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2").adm(receivedBid2Adm).ext(bidExt2),
                BidType.video, "bidder2");

        final EventsContext eventsContext = EventsContext.builder().auctionTimestamp(1000L).build();

        // when
        cacheService.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        // Second value is adm length for each
        verify(metrics, times(1)).updateCacheCreativeSize(eq(ACCOUNT_ID), eq(0));
        verify(metrics, times(2)).updateCacheCreativeSize(eq(ACCOUNT_ID), eq(4));

        final Bid bid1 = bidInfo1.getBid();
        final Bid bid2 = bidInfo2.getBid();

        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(3)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid1)).build(),
                        PutObject.builder().type("json").value(mapper.valueToTree(bid2)).build(),
                        PutObject.builder().type("xml").value(new TextNode(receivedBid2Adm)).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlAndSetTtlFromBid() throws IOException {
        // given
        final BidInfo bidInfo = givenBidInfo(
                bidBuilder -> bidBuilder.id("bidId1").exp(10),
                impBuilder -> impBuilder.id("impId1").exp(20));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .cacheBidsTtl(30)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlAndSetTtlFromImp() throws IOException {
        // given
        final BidInfo bidInfo = givenBidInfo(
                bidBuilder -> bidBuilder.id("bidId1"),
                impBuilder -> impBuilder.id("impId1").exp(10));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .cacheBidsTtl(20)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlAndSetTtlFromRequest() throws IOException {
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"))),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .cacheBidsTtl(10)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlAndSetTtlFromAccountBannerTtl()
            throws IOException {
        // given
        cacheService = new CacheService(
                CacheTtl.of(20, null),
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                vastModifier,
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"))),
                givenAuctionContext(accountBuilder -> accountBuilder.bannerCacheTtl(10), identity()),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlAndSetTtlFromMediaTypeTtl() throws IOException {
        // given
        cacheService = new CacheService(
                CacheTtl.of(10, null),
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                vastModifier,
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"))),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithTtlFromMediaTypeWhenAccountIsEmpty() throws IOException {
        // given
        cacheService = new CacheService(
                CacheTtl.of(10, null),
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                vastModifier,
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"))),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsExactly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithNoTtlAndSetEmptyTtl() throws IOException {
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"))),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsNull();

        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids().values())
                .flatExtracting(CacheInfo::getTtl)
                .containsNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBids() {
        // given
        final BidInfo bidInfo = givenBidInfo(bidBuilder -> bidBuilder.id("bidId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bidInfo.getBid(), CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForVideoBids() {
        // given
        final BidInfo bidInfo = givenBidInfo(bidBuilder -> bidBuilder.id("bidId1").adm("adm1"), BidType.video,
                "bidder");

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bidInfo),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bidInfo.getBid(), CacheInfo.of(null, "uuid1", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(
                        CacheObject.of("uuid1"),
                        CacheObject.of("uuid2"),
                        CacheObject.of("videoUuid1")))));

        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.video, "bidder1");
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2"), BidType.banner, "bidder2");

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(2)
                .containsOnly(
                        entry(bidInfo1.getBid(), CacheInfo.of("uuid1", "videoUuid1", null, null)),
                        entry(bidInfo2.getBid(), CacheInfo.of("uuid2", null, null, null)));
    }

    @Test
    public void cachePutObjectsShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<BidCacheResponse> future = cacheService.cachePutObjects(
                singletonList(PutObject.builder().build()), true, emptySet(), "", "",
                expiredTimeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isNotNull().hasMessage("Timeout has been exceeded");
    }

    @Test
    public void cachePutObjectsShouldReturnResultWithEmptyListWhenPutObjectsIsEmpty() {
        // when
        final Future<BidCacheResponse> result = cacheService.cachePutObjects(emptyList(), true,
                emptySet(), null, null, null);

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result.result().getResponses()).isEmpty();
    }

    @Test
    public void cachePutObjectsShouldModifyVastAndCachePutObjects() throws IOException {
        // given
        final PutObject firstPutObject = PutObject.builder()
                .type("xml")
                .bidid("bidId1")
                .bidder("bidder1")
                .timestamp(1L)
                .value(new TextNode("vast"))
                .build();
        final PutObject secondPutObject = PutObject.builder()
                .type("xml")
                .bidid("bidId2")
                .bidder("bidder2")
                .timestamp(1L)
                .value(new TextNode("VAST"))
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"))
                .willReturn(new TextNode("VAST"));

        // when
        cacheService.cachePutObjects(
                asList(firstPutObject, secondPutObject), true, singleton("bidder1"), "account", "pbjs", timeout);

        // then
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(12));
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(4));

        verify(vastModifier).modifyVastXml(true, singleton("bidder1"), firstPutObject, "account", "pbjs");
        verify(vastModifier).modifyVastXml(true, singleton("bidder1"), secondPutObject, "account", "pbjs");

        final PutObject modifiedFirstPutObject = firstPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .value(new TextNode("modifiedVast"))
                .build();
        final PutObject modifiedSecondPutObject = secondPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .build();

        assertThat(captureBidCacheRequest().getPuts()).hasSize(2)
                .containsOnly(modifiedFirstPutObject, modifiedSecondPutObject);
    }

    private AuctionContext givenAuctionContext(UnaryOperator<Account.AccountBuilder> accountCustomizer,
                                               UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        final Account.AccountBuilder accountBuilder = Account.builder()
                .id(ACCOUNT_ID);
        final BidRequest.BidRequestBuilder bidRequestBuilder = BidRequest.builder()
                .imp(singletonList(givenImp(identity())));
        return AuctionContext.builder()
                .account(accountCustomizer.apply(accountBuilder).build())
                .bidRequest(bidRequestCustomizer.apply(bidRequestBuilder).build())
                .timeout(timeout)
                .build();
    }

    private AuctionContext givenAuctionContext(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return givenAuctionContext(identity(), bidRequestCustomizer);
    }

    private AuctionContext givenAuctionContext() {
        return givenAuctionContext(identity(), identity());
    }

    private static Bid givenBidOpenrtb(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidInfo.builder()
                .bid(bidCustomizer.apply(Bid.builder()).build())
                .correspondingImp(givenImp(UnaryOperator.identity()))
                .bidder("bidder")
                .bidType(BidType.banner)
                .build();
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer,
                                        UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidInfo.builder()
                .bid(bidCustomizer.apply(Bid.builder()).build())
                .correspondingImp(impCustomizer.apply(Imp.builder()).build())
                .bidder("bidder")
                .bidType(BidType.banner)
                .build();
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer,
                                        BidType bidType,
                                        String bidder) {
        return BidInfo.builder()
                .bid(bidCustomizer.apply(Bid.builder()).build())
                .correspondingImp(givenImp(UnaryOperator.identity()))
                .bidder(bidder)
                .bidType(bidType)
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static CacheHttpRequest givenCacheHttpRequest(Bid... bids) throws JsonProcessingException {
        final List<PutObject> putObjects;
        if (bids != null) {
            putObjects = new ArrayList<>();
            for (Bid bid : bids) {
                putObjects.add(PutObject.builder().type("json").value(mapper.valueToTree(bid)).build());
            }
        } else {
            putObjects = null;
        }
        return CacheHttpRequest.of(
                "http://cache-service/cache",
                mapper.writeValueAsString(BidCacheRequest.of(putObjects)));
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, response);
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(anyString(), any(), captor.capture(), anyLong());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }
}
