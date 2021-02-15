package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.auction.model.GeneratedBidIds;
import org.prebid.server.auction.model.CachedDebugLog;
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
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
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
import java.util.Optional;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
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

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final CacheTtl mediaTypeCacheTtl = CacheTtl.of(null, null);

    @Mock
    private HttpClient httpClient;
    @Mock
    private EventsService eventsService;
    @Mock
    private Metrics metrics;
    @Mock
    private GeneratedBidIds allBidIds;
    @Mock
    private GeneratedBidIds videoCachedBidIds;
    @Mock
    private UUIDIdGenerator idGenerator;

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
                100L,
                eventsService,
                metrics,
                clock,
                idGenerator,
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
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        verify(httpClient).post(anyString(), any(), any(), eq(500L));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext().toBuilder().timeout(expiredTimeout).build(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isNotNull().hasMessage("Timeout has been exceeded");
        assertThat(result.getHttpCall()).isNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldStoreWinUrlWithGeneratedBidId() {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));
        final String generatedBidId = "GeneratedBidId";
        given(allBidIds.getGeneratedId(any(), any(), any())).willReturn(generatedBidId);
        given(allBidIds.getBidderForBid(any(), any())).willReturn(Optional.of("bidder"));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                EventsContext.builder().enabledForAccount(true).enabledForRequest(true).build());

        // then
        verify(eventsService).winUrl(eq(generatedBidId), eq("bidder"), eq("accountId"), isNull(), isNull());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateReadingHttpResponseFails() throws JsonProcessingException {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        verify(metrics).updateCacheRequestFailedTime(eq("accountId"), anyLong());

        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bid);
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder().requestUri(request.getUri()).requestBody(request.getBody())
                        .endpoint("http://cache-service/cache").responseTimeMillis(0).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseCodeIsNot200() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(503, "response");

        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bid);
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("HTTP status code 503");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder().endpoint("http://cache-service/cache")
                        .requestBody(request.getBody()).requestUri(request.getUri()).responseStatus(503)
                        .responseBody("response").responseTimeMillis(0).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseBodyCouldNotBeParsed() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "response");
        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bid);
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("Cannot parse response: response");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder().endpoint("http://cache-service/cache")
                        .requestUri(request.getUri()).requestBody(request.getBody())
                        .responseStatus(200).responseBody("response").responseTimeMillis(0).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateCacheEntriesNumberDoesNotMatchBidsNumber()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bid);
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isNotNull().isInstanceOf(PreBidException.class)
                .hasMessage("The number of response cache objects doesn't match with bids");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder().endpoint("http://cache-service/cache")
                        .requestBody(request.getBody()).requestUri(request.getUri())
                        .responseStatus(200).responseBody("{}").responseTimeMillis(0).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedDebugInfo() throws JsonProcessingException {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        final CacheHttpRequest request = givenCacheHttpRequest(bid);
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(DebugHttpCall.builder().endpoint("http://cache-service/cache")
                        .requestUri(request.getUri()).requestBody(request.getBody())
                        .responseStatus(200).responseBody("{\"responses\":[{\"uuid\":\"uuid1\"}]}")
                        .responseTimeMillis(0).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedCacheBids() {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        final Bid bid1 = givenBidOpenrtb(builder -> builder.id("bid1").impid("impId1"));
        final Bid bid2 = givenBidOpenrtb(builder -> builder.id("bid2").impid("impId2")
                .adm("adm2"));
        final Imp imp1 = givenImp(identity());
        final Imp imp2 = givenImp(builder -> builder.id("impId2").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(
                        accountBuilder -> accountBuilder.id("accountId"),
                        bidRequestBuilder -> bidRequestBuilder
                                .imp(asList(imp1, imp2))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                EventsContext.builder().auctionTimestamp(1000L).build());

        // then
        verify(metrics, times(1)).updateCacheCreativeSize(eq("accountId"), eq(0));
        verify(metrics, times(2)).updateCacheCreativeSize(eq("accountId"), eq(4));

        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(3)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid1)).build(),
                        PutObject.builder().type("json").value(mapper.valueToTree(bid2)).build(),
                        PutObject.builder().type("xml").value(new TextNode(bid2.getAdm())).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlAndSetTtlFromBid() throws IOException {
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(builder -> builder.impid("impId1").exp(10))),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(buider -> buider.id("impId1").exp(20))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(buider -> buider.exp(10))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
                100L,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(
                        accountBuilder -> accountBuilder.bannerCacheTtl(10),
                        identity()),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
        cacheService = new CacheService(
                CacheTtl.of(10, null),
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
        cacheService = new CacheService(
                CacheTtl.of(10, null),
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
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
        final Bid bid = givenBidOpenrtb(identity());

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForVideoBids() {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Imp imp = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp))),
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheInfo.of(null, "uuid1", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldAddDebugLogCacheAlongWithBids() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Imp imp = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final AuctionContext auctionContext = givenAuctionContext(
                bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp)))
                .toBuilder().cachedDebugLog(new CachedDebugLog(true, 100, null, jacksonMapper)).build();

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                auctionContext,
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .extracting(PutObject::getKey)
                .contains("log_null");
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(CacheObject.of("uuid1"), CacheObject.of("uuid2"),
                        CacheObject.of("videoUuid1"), CacheObject.of("videoUuid2")))));

        final Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id("impId2").video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(asList(imp1, imp2))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(2)
                .containsOnly(
                        entry(bid1, CacheInfo.of("uuid1", "videoUuid1", null, null)),
                        entry(bid2, CacheInfo.of("uuid2", "videoUuid2", null, null)));
    }

    @Test
    public void cacheBidsOpenrtbShouldNotCacheVideoBidWithMissingImpId() {
        // given
        final Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id(null).video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(asList(imp1, imp2))),
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid1, CacheInfo.of(null, "uuid1", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldWrapEmptyAdmFieldUsingNurlFieldValue() throws IOException {
        // given
        final Bid bid1 = givenBidOpenrtb(builder -> builder.id("bid1").impid("impId1")
                .adm("adm1"));
        final Bid bid2 = givenBidOpenrtb(builder -> builder.id("bid2").impid("impId1")
                .nurl("adm2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(4)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid1)).build(),
                        PutObject.builder().type("json").value(mapper.valueToTree(bid2)).build(),
                        PutObject.builder().type("xml").value(new TextNode("adm1")).build(),
                        PutObject.builder().type("xml").value(new TextNode(
                                "<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>prebid.org wrapper</AdSystem>"
                                        + "<VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI><Impression></Impression>"
                                        + "<Creatives></Creatives></Wrapper></Ad></VAST>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotModifyVastXmlWhenBidIdIsNotInToModifyList() throws IOException {
        // given
        final Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid)).build(),
                        PutObject.builder().type("xml").value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldUpdateVastXmlPutObjectWithKeyWhenBidHasCategoryDuration() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        given(idGenerator.generateId()).willReturn("randomId");
        given(videoCachedBidIds.getGeneratedId(any(), any(), any())).willReturn("bid2");
        given(videoCachedBidIds.getBidderForBid(any(), any())).willReturn(Optional.of("bidder"));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .biddersToBidsCategories(singletonMap("bidder", singletonMap("bid1", "bid1Category")))
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid)).build(),
                        PutObject.builder().key("bid1Category_randomId").type("xml")
                                .value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotUpdateVastXmlPutObjectWithKeyWhenBidderIsWasNotFound() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        given(idGenerator.generateId()).willReturn("randomId");

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .biddersToBidsCategories(singletonMap("bidder", singletonMap("bid1", "bid1Category")))
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid)).build(),
                        PutObject.builder().type("xml")
                                .value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotUpdateVastXmlPutObjectWithKeyWhenDoesNotHaveCatDur() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        given(idGenerator.generateId()).willReturn("randomId");

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .biddersToBidsCategories(singletonMap("bidder", emptyMap()))
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid)).build(),
                        PutObject.builder().type("xml").value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotGenerateHbCacheIdAndCreateKeyWhenCategoriesMapIsEmpty() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        given(idGenerator.generateId()).willReturn("randomId");

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .biddersToBidsCategories(null)
                        .build(),
                eventsContext);

        // then
        verifyZeroInteractions(idGenerator);
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid)).build(),
                        PutObject.builder().type("xml")
                                .value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldRemoveCatDurPrefixFromVideoUuidFromResponse() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(CacheObject.of("uuid"), CacheObject.of("catDir_randomId")))));

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        given(idGenerator.generateId()).willReturn("randomId");

        // when
        final Future<CacheServiceResult> resultFuture = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .biddersToBidsCategories(singletonMap("bidder", singletonMap("bid1", "catDir")))
                        .build(),
                eventsContext);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheInfo.of("uuid", "randomId", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldNotAddTrackingImpToBidAdmWhenXmlDoesNotContainImpTag() throws IOException {
        // given
        final Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("no impression tag"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                eventsContext);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(mapper.valueToTree(bid)).build(),
                        PutObject.builder().type("xml").value(new TextNode("no impression tag")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldAddTrackingLinkToImpTagWhenItIsEmpty() throws IOException {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder
                .id("bid1")
                .impid("impId1")
                .adm("<Impression></Impression>"));
        final Imp imp1 = givenImp(builder -> builder
                .id("impId1")
                .video(Video.builder().build()));

        final String generatedBidId = "generatedBidId";
        final String bidder = "bidder";
        given(videoCachedBidIds.getGeneratedId(any(), any(), any())).willReturn(generatedBidId);
        given(videoCachedBidIds.getBidderForBid(any(), any())).willReturn(Optional.of(bidder));

        final String vastUrl = String.format("https://test-event.com/event?t=imp&b=%s&f=b&a=accountId", generatedBidId);
        given(eventsService.vastUrlTracking(anyString(), anyString(), any(), any(), any()))
                .willReturn(vastUrl);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                EventsContext.builder().enabledForAccount(true).enabledForRequest(false).build());

        // then
        verify(eventsService).vastUrlTracking(eq(generatedBidId), eq(bidder), any(), any(), any());

        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder()
                                .type("json")
                                .value(mapper.valueToTree(bid))
                                .build(),
                        PutObject.builder()
                                .type("xml")
                                .value(new TextNode("<Impression><![CDATA[" + vastUrl + "]]></Impression>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldAddTrackingImpToBidAdmXmlWhenThatBidShouldBeModifiedAndContainsImpTag()
            throws IOException {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder
                .id("bid1")
                .impid("impId1")
                .adm("<Impression>http:/test.com</Impression>"));
        final Imp imp1 = givenImp(builder -> builder
                .id("impId1")
                .video(Video.builder().build()));

        final String generatedBidId = "generatedBidId";
        final String bidder = "bidder";
        given(videoCachedBidIds.getGeneratedId(any(), any(), any())).willReturn(generatedBidId);
        given(videoCachedBidIds.getBidderForBid(any(), any())).willReturn(Optional.of(bidder));

        final String vastUrl = String.format("https://test-event.com/event?t=imp&b=%s&f=b&a=accountId", generatedBidId);
        given(eventsService.vastUrlTracking(anyString(), anyString(), any(), any(), any()))
                .willReturn(vastUrl);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                EventsContext.builder().enabledForAccount(true).enabledForRequest(false).build());

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder()
                                .type("json")
                                .value(mapper.valueToTree(bid))
                                .build(),
                        PutObject.builder()
                                .type("xml")
                                .value(new TextNode("<Impression>http:/test.com</Impression>"
                                        + "<Impression><![CDATA[" + vastUrl + "]]></Impression>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotAddTrackingImpWhenEventsNotEnabled() throws IOException {
        // given
        final Bid bid = givenBidOpenrtb(builder -> builder
                .id("bid1")
                .impid("impId1")
                .adm("<Impression>http:/test.com</Impression>"));
        final Imp imp1 = givenImp(builder -> builder
                .id("impId1")
                .video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoGeneratedBidIdsToModify(videoCachedBidIds)
                        .bidderToBidsToGeneratedIds(allBidIds)
                        .build(),
                EventsContext.builder().enabledForAccount(false).build());

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder()
                                .type("json")
                                .value(mapper.valueToTree(bid))
                                .build(),
                        PutObject.builder()
                                .type("xml")
                                .value(new TextNode("<Impression>http:/test.com</Impression>"))
                                .build());
        verifyZeroInteractions(eventsService);
    }

    @Test
    public void cachePutObjectsShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<BidCacheResponse> future = cacheService.cachePutObjects(
                singletonList(PutObject.builder().build()), emptySet(), "", "", expiredTimeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isNotNull().hasMessage("Timeout has been exceeded");
    }

    @Test
    public void cachePutObjectsShouldReturnResultWithEmptyListWhenPutObjectsIsEmpty() {
        // when
        final Future<BidCacheResponse> result = cacheService.cachePutObjects(emptyList(), emptySet(), null, null, null);

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
                .value(new TextNode("<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>"
                        + "prebid.org wrapper</AdSystem><VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI><Impression>"
                        + "</Impression><Creatives></Creatives></Wrapper></Ad></VAST>"))
                .build();
        final PutObject secondPutObject = PutObject.builder()
                .type("xml")
                .bidid("bidId2")
                .bidder("bidder2")
                .timestamp(1L)
                .value(new TextNode("VAST"))
                .build();

        given(eventsService.vastUrlTracking(any(), any(), any(), any(), anyString()))
                .willReturn("http://external-url/event");

        // when
        cacheService.cachePutObjects(
                asList(firstPutObject, secondPutObject), singleton("bidder1"), "account", "pbjs", timeout);

        // then
        verify(metrics, times(1)).updateCacheCreativeSize(eq("account"), eq(224));
        verify(metrics, times(1)).updateCacheCreativeSize(eq("account"), eq(4));

        final PutObject modifiedFirstPutObject = firstPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .value(new TextNode("<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>"
                        + "prebid.org wrapper</AdSystem><VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI>"
                        + "<Impression><!"
                        + "[CDATA[http://external-url/event]]>"
                        + "</Impression><Creatives></Creatives></Wrapper></Ad></VAST>"))
                .build();
        final PutObject modifiedSecondPutObject = secondPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .timestamp(null)
                .build();

        assertThat(captureBidCacheRequest().getPuts()).hasSize(2)
                .containsOnly(modifiedFirstPutObject, modifiedSecondPutObject);
    }

    @Test
    public void cachePutObjectsShouldCallEventsServiceWithExpectedArguments() {
        // given
        final PutObject firstPutObject = PutObject.builder()
                .type("xml")
                .bidid("bidId1")
                .bidder("bidder1")
                .timestamp(1000L)
                .value(new TextNode("<Impression></Impression>"))
                .build();

        // when
        cacheService.cachePutObjects(singletonList(firstPutObject), singleton("bidder1"), "account", "pbjs", timeout);

        // then
        verify(eventsService).vastUrlTracking(eq("bidId1"), eq("bidder1"), eq("account"), eq(1000L), eq("pbjs"));
    }

    @Test
    public void cacheVideoDebugLogShouldCacheDebugLogWithItsOwnKey() {
        // given
        final CachedDebugLog cachedDebugLog = new CachedDebugLog(true, 100, null, jacksonMapper);
        cachedDebugLog.setCacheKey("cacheKey");

        // when
        final String cacheKey = cacheService.cacheVideoDebugLog(cachedDebugLog, 1000);

        // then
        assertThat(cacheKey).isEqualTo("cacheKey");
        verify(httpClient).post(anyString(), any(), anyString(), anyLong());
    }

    @Test
    public void cacheVideoDebugLogShouldCacheDebugLogWithGeneratedKey() {
        // given
        final CachedDebugLog cachedDebugLog = new CachedDebugLog(true, 100, null, jacksonMapper);
        given(idGenerator.generateId()).willReturn("generatedKey");
        // when
        final String cacheKey = cacheService.cacheVideoDebugLog(cachedDebugLog, 1000);

        // then
        assertThat(cacheKey).isEqualTo("generatedKey");
        verify(httpClient).post(anyString(), any(), anyString(), anyLong());
    }

    private AuctionContext givenAuctionContext(UnaryOperator<Account.AccountBuilder> accountCustomizer,
                                               UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        final Account.AccountBuilder accountBuilder = Account.builder()
                .id("accountId");
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
