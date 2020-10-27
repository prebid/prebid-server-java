package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.cache.proto.request.BannerValue;
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
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
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
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
    public void cacheBidsShouldNeverCallCacheServiceIfNoBidsPassed() {
        // when
        final List<BidCacheResult> result = cacheService.cacheBids(emptyList(), timeout, "accountId").result();

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        verify(httpClient).post(anyString(), any(), any(), eq(500L));
    }

    @Test
    public void cacheBidsShouldFailIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), expiredTimeout, "accountId");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class);
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void cacheBidsShouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class)
                .hasMessage("Response exception");
    }

    @Test
    public void cacheBidsShouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        verify(metrics).updateCacheRequestFailedTime(eq("accountId"), anyLong());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("HTTP status code 503");
    }

    @Test
    public void cacheBidsShouldFailIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        verify(metrics).updateCacheRequestFailedTime(eq("accountId"), anyLong());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class);
    }

    @Test
    public void cacheBidsShouldFailIfCacheEntriesNumberDoesNotMatchBidsNumber() {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("The number of response cache objects doesn't match with bids");
    }

    @Test
    public void cacheBidsShouldMakeHttpRequestUsingConfigurationParams() throws MalformedURLException {
        // given
        cacheService = new CacheService(
                mediaTypeCacheTtl,
                httpClient,
                new URL("https://cache-service-host:8888/cache"),
                "https://cache-service-host:8080/cache?uuid=",
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        verify(httpClient).post(eq("https://cache-service-host:8888/cache"), any(), any(), anyLong());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedBody() throws Exception {
        // given
        final String adm3 = "<script type=\"application/javascript\" src=\"http://nym1-ib.adnxs"
                + "f3919239&pp=${AUCTION_PRICE}&\"></script>";
        final String adm4 = "<img src=\"https://tpp.hpppf.com/simgad/11261207092432736464\" border=\"0\" "
                + "width=\"184\" height=\"90\" alt=\"\" class=\"img_ad\">";

        // when
        cacheService.cacheBids(asList(
                givenBid(builder -> builder.adm("adm1").nurl("nurl1").height(100).width(200)),
                givenBid(builder -> builder.adm("adm2").nurl("nurl2").height(300).width(400)),
                givenBid(builder -> builder.adm(adm3).mediaType(MediaType.video)),
                givenBid(builder -> builder.adm(adm4).mediaType(MediaType.video))),
                timeout,
                "accountId");

        // then
        verify(metrics, times(2)).updateCacheCreativeSize(eq("accountId"), eq(4));
        verify(metrics, times(1)).updateCacheCreativeSize(eq("accountId"), eq(103));
        verify(metrics, times(1)).updateCacheCreativeSize(eq("accountId"), eq(118));

        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(4)
                .containsOnly(
                        PutObject.builder().type("json").value(
                                mapper.valueToTree(BannerValue.of("adm1", "nurl1", 200, 100))).build(),
                        PutObject.builder().type("json").value(
                                mapper.valueToTree(BannerValue.of("adm2", "nurl2", 400, 300))).build(),
                        PutObject.builder().type("xml").value(new TextNode(adm3)).build(),
                        PutObject.builder().type("xml").value(new TextNode(adm4)).build());
    }

    @Test
    public void cacheBidsShouldReturnExpectedResult() {
        // given and when
        final Future<List<BidCacheResult>> future = cacheService.cacheBids(singleBidList(), timeout, "accountId");

        // then
        verify(metrics).updateCacheRequestSuccessTime(eq("accountId"), anyLong());

        final List<BidCacheResult> bidCacheResults = future.result();
        assertThat(bidCacheResults).hasSize(1)
                .containsOnly(BidCacheResult.of("uuid1", "http://cache-service-host/cache?uuid=uuid1"));
    }

    @Test
    public void cacheBidsVideoOnlyShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // when
        cacheService.cacheBidsVideoOnly(asList(
                givenBid(builder -> builder.mediaType(MediaType.banner).adm("adm1")),
                givenBid(builder -> builder.mediaType(MediaType.video).adm("adm2"))),
                timeout,
                "accountId");

        // then
        verify(metrics, times(1)).updateCacheCreativeSize(eq("accountId"), eq(4));

        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .containsOnly(PutObject.builder().type("xml").value(new TextNode("adm2")).build());
    }

    @Test
    public void cacheBidsVideoOnlyShouldReturnExpectedResult() {
        // given and when
        final Future<List<BidCacheResult>> future = cacheService.cacheBidsVideoOnly(
                singletonList(givenBid(builder -> builder.mediaType(MediaType.video))), timeout, "accountId");

        // then
        final List<BidCacheResult> bidCacheResults = future.result();
        assertThat(bidCacheResults).hasSize(1)
                .containsOnly(BidCacheResult.of("uuid1", "http://cache-service-host/cache?uuid=uuid1"));
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
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
                        .build(),
                EventsContext.builder().enabledForAccount(true).enabledForRequest(true).build());

        // then
        verify(eventsService).winUrl(eq("bidId1"), eq("bidder"), eq("accountId"), isNull(), isNull());
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateReadingHttpResponseFails() throws JsonProcessingException {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
                        .build(),
                eventsContext);

        // then
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

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(givenImp(builder -> builder.id("impId1"))))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder", singletonList("bidId1")))
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
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.id("bid1").impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.id("bid2").impid("impId2")
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
                        .bidderToVideoBidIdsToModify(singletonMap("bidder2", singletonList("bid2")))
                        .bidderToBidIds(singletonMap("bidder1", asList("bid1", "bid2")))
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
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(
                        accountBuilder -> accountBuilder.bannerCacheTtl(10),
                        identity()),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
                eventsService,
                metrics,
                clock,
                jacksonMapper);

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(identity());

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheInfo.of("uuid1", null, null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForVideoBids() {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Imp imp = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp))),
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder1", singletonList("bidId1")))
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheInfo.of(null, "uuid1", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(CacheObject.of("uuid1"), CacheObject.of("uuid2"),
                        CacheObject.of("videoUuid1"), CacheObject.of("videoUuid2")))));

        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id("impId2").video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(asList(imp1, imp2))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder1", singletonList("bidId1")))
                        .bidderToBidIds(singletonMap("bidder2", singletonList("bidId2")))
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
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id(null).video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(asList(imp1, imp2))),
                CacheContext.builder()
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder1", singletonList("bidId1")))
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid1, CacheInfo.of(null, "uuid1", null, null));
    }

    @Test
    public void cacheBidsOpenrtbShouldWrapEmptyAdmFieldUsingNurlFieldValue() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.id("bid1").impid("impId1")
                .adm("adm1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.id("bid2").impid("impId1")
                .nurl("adm2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder1", singletonList("bid1")))
                        .bidderToBidIds(singletonMap("bidder1", asList("bid1", "bid2")))
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
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder", singletonList("bid2")))
                        .bidderToBidIds(singletonMap("bidder", singletonList("bid1")))
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
    public void cacheBidsOpenrtbShouldNotAddTrackingImpToBidAdmWhenXmlDoesNotContainImpTag() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("no impression tag"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder", singletonList("bid2")))
                        .bidderToBidIds(singletonMap("bidder", singletonList("bid1")))
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
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder
                .id("bid1")
                .impid("impId1")
                .adm("<Impression></Impression>"));
        final Imp imp1 = givenImp(builder -> builder
                .id("impId1")
                .video(Video.builder().build()));

        given(eventsService.vastUrlTracking(anyString(), anyString(), any(), any(), any()))
                .willReturn("https://test-event.com/event?t=imp&b=bid1&f=b&a=accountId");

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder", singletonList("bid1")))
                        .bidderToBidIds(singletonMap("bidder", singletonList("bid1")))
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
                                .value(new TextNode("<Impression><![CDATA[https://test-event.com/event?t=imp&"
                                        + "b=bid1&f=b&a=accountId]]></Impression>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldAddTrackingImpToBidAdmXmlWhenThatBidShouldBeModifiedAndContainsImpTag()
            throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder
                .id("bid1")
                .impid("impId1")
                .adm("<Impression>http:/test.com</Impression>"));
        final Imp imp1 = givenImp(builder -> builder
                .id("impId1")
                .video(Video.builder().build()));

        given(eventsService.vastUrlTracking(any(), any(), any(), any(), any()))
                .willReturn("https://test-event.com/event?t=imp&b=bid1&f=b&a=accountId");

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid),
                givenAuctionContext(bidRequestBuilder -> bidRequestBuilder.imp(singletonList(imp1))),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .bidderToVideoBidIdsToModify(singletonMap("bidder", singletonList("bid1")))
                        .bidderToBidIds(singletonMap("bidder", singletonList("bid1")))
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
                                .value(new TextNode("<Impression>http:/test.com</Impression><Impression>"
                                        + "<![CDATA[https://test-event.com/event?t=imp&b=bid1&f=b&a=accountId]]>"
                                        + "</Impression>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotAddTrackingImpWhenEventsNotEnabled() throws IOException {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder
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
                        .bidderToVideoBidIdsToModify(singletonMap("bidder", singletonList("bid1")))
                        .bidderToBidIds(singletonMap("bidder", singletonList("bid1")))
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

    private static List<Bid> singleBidList() {
        return singletonList(givenBid(identity()));
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static com.iab.openrtb.response.Bid givenBidOpenrtb(
            UnaryOperator<com.iab.openrtb.response.Bid.BidBuilder> bidCustomizer) {

        return bidCustomizer.apply(com.iab.openrtb.response.Bid.builder()).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static CacheHttpRequest givenCacheHttpRequest(com.iab.openrtb.response.Bid... bids)
            throws JsonProcessingException {
        final List<PutObject> putObjects;
        if (bids != null) {
            putObjects = new ArrayList<>();
            for (com.iab.openrtb.response.Bid bid : bids) {
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
