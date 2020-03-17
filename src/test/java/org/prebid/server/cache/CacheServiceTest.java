package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpCall;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.cache.proto.request.BannerValue;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
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

    private Timeout timeout;

    private Timeout expiredTimeout;

    private Account account;

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

        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);

        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        account = Account.builder().build();

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
        final List<BidCacheResult> result = cacheService.cacheBids(emptyList(), timeout).result();

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(httpClient).post(anyString(), any(), any(), eq(500L));
    }

    @Test
    public void cacheBidsShouldFailIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), expiredTimeout);

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
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout);

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
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(metrics).updateCacheRequestFailedTime(anyLong());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("HTTP status code 503");
    }

    @Test
    public void cacheBidsShouldFailIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(metrics).updateCacheRequestFailedTime(anyLong());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class);
    }

    @Test
    public void cacheBidsShouldFailIfCacheEntriesNumberDoesNotMatchBidsNumber() {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout);

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
        cacheService.cacheBids(singleBidList(), timeout);

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
                timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(4)
                .containsOnly(
                        PutObject.builder().type("json").value(
                                mapper.valueToTree(BannerValue.of("adm1", "nurl1", 200, 100))).build(),
                        PutObject.builder().type("json").value(
                                mapper.valueToTree(BannerValue.of("adm2", "nurl2", 400, 300))).build(),
                        PutObject.builder().type("xml").value(new TextNode(adm3)).build(),
                        PutObject.builder().type("xml").value(new TextNode(adm4)).build()
                );
    }

    @Test
    public void cacheBidsShouldReturnExpectedResult() {
        // given and when
        final Future<List<BidCacheResult>> future = cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(metrics).updateCacheRequestSuccessTime(anyLong());

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
                timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .containsOnly(PutObject.builder().type("xml").value(new TextNode("adm2")).build());
    }

    @Test
    public void cacheBidsVideoOnlyShouldReturnExpectedResult() {
        // given and when
        final Future<List<BidCacheResult>> future = cacheService.cacheBidsVideoOnly(
                singletonList(givenBid(builder -> builder.mediaType(MediaType.video))), timeout);

        // then
        final List<BidCacheResult> bidCacheResults = future.result();
        assertThat(bidCacheResults).hasSize(1)
                .containsOnly(BidCacheResult.of("uuid1", "http://cache-service-host/cache?uuid=uuid1"));
    }

    @Test
    public void cacheBidsOpenrtbShouldNeverCallCacheServiceIfNoBidsPassed() {
        // when
        cacheService.cacheBidsOpenrtb(emptyList(), emptyList(), null, null, null);

        // then
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBidsOpenrtb(singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        verify(httpClient).post(anyString(), any(), any(), eq(500L));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(), account, expiredTimeout);

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
        account = Account.builder().id("accountId").build();

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), Account.builder().id("accountId").build(),
                timeout);

        // then
        verify(eventsService).winUrl(eq("bidId1"), eq("accountId"));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateReadingHttpResponseFails() throws JsonProcessingException {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(CacheHttpCall.of(givenCacheHttpRequest(bid), null, 0));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseCodeIsNot200() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(503, "response");
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("HTTP status code 503");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(CacheHttpCall.of(givenCacheHttpRequest(bid),
                        CacheHttpResponse.of(503, "response"), 0));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateResponseBodyCouldNotBeParsed() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "response");
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isInstanceOf(PreBidException.class).hasMessage("Cannot parse response: response");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(CacheHttpCall.of(givenCacheHttpRequest(bid),
                        CacheHttpResponse.of(200, "response"), 0));
    }

    @Test
    public void cacheBidsOpenrtbShouldTolerateCacheEntriesNumberDoesNotMatchBidsNumber()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, "{}");
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).isEmpty();
        assertThat(result.getError()).isNotNull().isInstanceOf(PreBidException.class)
                .hasMessage("The number of response cache objects doesn't match with bids");
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(CacheHttpCall.of(givenCacheHttpRequest(bid),
                        CacheHttpResponse.of(200, "{}"), 0));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedDebugInfo() throws JsonProcessingException {
        // given
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getHttpCall()).isNotNull()
                .isEqualTo(CacheHttpCall.of(givenCacheHttpRequest(bid),
                        CacheHttpResponse.of(200, "{\"responses\":[{\"uuid\":\"uuid1\"}]}"), 0));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedCacheBids() {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.id("bidId1").impid("impId1"));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(builder -> builder.id("impId1"))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final CacheServiceResult result = future.result();
        assertThat(result.getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheIdInfo.of("uuid1", null));
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        given(eventsService.winUrl(any(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2").adm("adm2"));
        final Imp imp1 = givenImp(identity());
        final Imp imp2 = givenImp(builder -> builder.id("impId2").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), asList(imp1, imp2),
                CacheContext.builder().shouldCacheBids(true).shouldCacheVideoBids(true).build(), account, timeout);

        // then
        final ObjectNode bidObjectNode1 = mapper.valueToTree(bid1);
        bidObjectNode1.put("wurl", "http://win-url");
        final ObjectNode bidObjectNode2 = mapper.valueToTree(bid2);
        bidObjectNode2.put("wurl", "http://win-url");
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(3)
                .containsOnly(
                        PutObject.builder().type("json").value(bidObjectNode1).build(),
                        PutObject.builder().type("json").value(bidObjectNode2).build(),
                        PutObject.builder().type("xml").value(new TextNode(bid2.getAdm())).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromBid() throws IOException {
        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(builder -> builder.impid("impId1").exp(10))),
                singletonList(givenImp(buider -> buider.id("impId1").exp(20))),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromImp() throws IOException {
        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(buider -> buider.exp(10))),
                CacheContext.builder().shouldCacheBids(true).cacheBidsTtl(20).build(), account, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromRequest() throws IOException {
        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).cacheBidsTtl(10).build(), account, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromAccountBannerTtl() throws IOException {
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
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(),
                Account.builder().bannerCacheTtl(10).build(), timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromMediaTypeTtl() throws IOException {
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
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
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
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithNoTtl() throws IOException {
        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBids() {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(identity());

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(givenImp(identity())),
                CacheContext.builder().shouldCacheBids(true).build(), account, timeout);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheIdInfo.of("uuid1", null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForVideoBids() {
        // given
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Imp imp = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(imp),
                CacheContext.builder().shouldCacheVideoBids(true).build(), account, timeout);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid, CacheIdInfo.of(null, "uuid1"));
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
                asList(bid1, bid2), asList(imp1, imp2),
                CacheContext.builder().shouldCacheBids(true).shouldCacheVideoBids(true).build(), account, timeout);

        // then
        assertThat(future.result().getCacheBids()).hasSize(2)
                .containsOnly(
                        entry(bid1, CacheIdInfo.of("uuid1", "videoUuid1")),
                        entry(bid2, CacheIdInfo.of("uuid2", "videoUuid2")));
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
                asList(bid1, bid2), asList(imp1, imp2),
                CacheContext.builder().shouldCacheVideoBids(true).build(), account, timeout);

        // then
        assertThat(future.result().getCacheBids()).hasSize(1)
                .containsEntry(bid1, CacheIdInfo.of(null, "uuid1"));
    }

    @Test
    public void cacheBidsOpenrtbShouldWrapEmptyAdMFieldUsingNurlFieldValue() throws IOException {
        // given
        given(eventsService.winUrl(any(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1").adm("adm1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId1").nurl("adm2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), singletonList(imp1),
                CacheContext.builder().shouldCacheBids(true).shouldCacheVideoBids(true).build(), account, timeout);

        // then
        final ObjectNode bidObjectNode1 = mapper.valueToTree(bid1);
        bidObjectNode1.put("wurl", "http://win-url");
        final ObjectNode bidObjectNode2 = mapper.valueToTree(bid2);
        bidObjectNode2.put("wurl", "http://win-url");
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(4)
                .containsOnly(
                        PutObject.builder().type("json").value(bidObjectNode1).build(),
                        PutObject.builder().type("json").value(bidObjectNode2).build(),
                        PutObject.builder().type("xml").value(new TextNode("adm1")).build(),
                        PutObject.builder().type("xml").value(new TextNode(
                                "<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>prebid.org wrapper</AdSystem>" +
                                        "<VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI><Impression></Impression>" +
                                        "<Creatives></Creatives></Wrapper></Ad></VAST>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotModifyVastXmlWhenBidIdIsNotInToModifyList() throws IOException {
        // given
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("adm"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(singletonList(bid), singletonList(imp1), CacheContext.builder()
                .shouldCacheBids(true).shouldCacheVideoBids(true).videoBidIdsToModify(singletonList("bid2"))
                .build(), account, timeout);

        // then
        final ObjectNode bidObjectNode = mapper.valueToTree(bid);
        bidObjectNode.put("wurl", "http://win-url");
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(bidObjectNode).build(),
                        PutObject.builder().type("xml").value(new TextNode("adm")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldNotAddTrackingImpToBidAdmWhenXmlDoesNotContainImpTag() throws IOException {
        // given
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("no impression tag"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(singletonList(bid), singletonList(imp1), CacheContext.builder()
                .shouldCacheBids(true).shouldCacheVideoBids(true).videoBidIdsToModify(singletonList("bid1"))
                .build(), account, timeout);

        // then
        final ObjectNode bidObjectNode = mapper.valueToTree(bid);
        bidObjectNode.put("wurl", "http://win-url");
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder().type("json").value(bidObjectNode).build(),
                        PutObject.builder().type("xml").value(new TextNode("no impression tag")).build());
    }

    @Test
    public void cacheBidsOpenrtbShouldAddTrackingLinkToImpTagWhenItIsEmpty() throws IOException {
        // given
        given(eventsService.vastUrlTracking(any(), any()))
                .willReturn("https://test-event.com/event?t=imp&b=bid1&f=b&a=accountId");
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("<Impression></Impression>"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        cacheService.cacheBidsOpenrtb(singletonList(bid), singletonList(imp1), CacheContext.builder()
                .shouldCacheBids(true).shouldCacheVideoBids(true).videoBidIdsToModify(singletonList("bid1"))
                .build(), account, timeout);
        final ObjectNode bidObjectNode = mapper.valueToTree(bid);
        bidObjectNode.put("wurl", "http://win-url");

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder()
                                .type("json")
                                .value(bidObjectNode)
                                .build(),
                        PutObject.builder()
                                .type("xml")
                                .value(new TextNode("<Impression><![CDATA[https://test-event.com/event?t=imp&" +
                                        "b=bid1&f=b&a=accountId]]></Impression>"))
                                .build());
    }

    @Test
    public void cacheBidsOpenrtbShouldAddTrackingImpToBidAdmXmlWhenThatBidShouldBeModifiedAndContainsImpTag()
            throws IOException {
        // given
        given(eventsService.vastUrlTracking(any(), any()))
                .willReturn("https://test-event.com/event?t=imp&b=bid1&f=b&a=accountId");
        given(eventsService.winUrl(anyString(), any())).willReturn("http://win-url");

        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder ->
                builder.id("bid1").impid("impId1").adm("<Impression>http:/test.com</Impression>"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));

        // when
        account = Account.builder().id("accountId").build();
        cacheService.cacheBidsOpenrtb(singletonList(bid), singletonList(imp1), CacheContext.builder()
                .shouldCacheBids(true).shouldCacheVideoBids(true).videoBidIdsToModify(singletonList("bid1"))
                .build(), account, timeout);
        final ObjectNode bidObjectNode = mapper.valueToTree(bid);
        bidObjectNode.put("wurl", "http://win-url");

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.builder()
                                .type("json")
                                .value(bidObjectNode)
                                .build(),
                        PutObject.builder()
                                .type("xml")
                                .value(new TextNode("<Impression>http:/test.com</Impression><Impression>" +
                                        "<![CDATA[https://test-event.com/event?t=imp&b=bid1&f=b&a=accountId]]>"
                                        + "</Impression>"))
                                .build());
    }

    @Test
    public void cachePutObjectsShouldTolerateGlobalTimeoutAlreadyExpired() {
        // when
        final Future<BidCacheResponse> future = cacheService.cachePutObjects(singletonList(PutObject.builder().build()),
                emptySet(), "", expiredTimeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isNotNull().hasMessage("Timeout has been exceeded");
    }

    @Test
    public void cachePutObjectsShouldReturnResultWithEmptyListWhenPutObjectsIsEmpty() {
        // when
        final Future<BidCacheResponse> result = cacheService.cachePutObjects(emptyList(), emptySet(), null, null);

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result.result().getResponses()).isEmpty();
    }

    @Test
    public void cachePutObjectsShouldModifyVastAndCachePutObjects() throws IOException {
        // given
        final PutObject firstPutObject = PutObject.builder()
                .type("xml")
                .bidid("biddid1")
                .bidder("bidder1")
                .value(new TextNode("<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>" +
                        "prebid.org wrapper</AdSystem><VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI><Impression>" +
                        "</Impression><Creatives></Creatives></Wrapper></Ad></VAST>")).build();
        final PutObject secondPutObject = PutObject.builder()
                .type("xml")
                .value(new TextNode("VAST"))
                .bidid("biddid2")
                .bidder("bidder2")
                .build();

        given(eventsService.vastUrlTracking(any(), any()))
                .willReturn("https://test-event.com/event?t=imp&b=biddid1&f=b&a=account");

        // when
        cacheService.cachePutObjects(Arrays.asList(firstPutObject, secondPutObject), singleton("bidder1"), "account",
                timeout);

        // then
        final PutObject modifiedSecondPutObject = firstPutObject.toBuilder()
                .value(new TextNode("<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>" +
                        "prebid.org wrapper</AdSystem><VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI>" +
                        "<Impression><![CDATA[https://test-event.com/event?t=imp&b=biddid1&f=b&a=account]]>" +
                        "</Impression><Creatives></Creatives></Wrapper></Ad></VAST>"))
                .build();
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2).containsOnly(modifiedSecondPutObject, secondPutObject);
    }

    private static List<Bid> singleBidList() {
        return singletonList(givenBid(identity()));
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static com.iab.openrtb.response.Bid givenBidOpenrtb(
            Function<com.iab.openrtb.response.Bid.BidBuilder, com.iab.openrtb.response.Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(com.iab.openrtb.response.Bid.builder()).build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static CacheHttpRequest givenCacheHttpRequest(com.iab.openrtb.response.Bid... bids)
            throws JsonProcessingException {
        final List<PutObject> putObjects;
        if (bids != null) {
            putObjects = new ArrayList<>();
            for (com.iab.openrtb.response.Bid bid : bids) {
                final ObjectNode bidObjectNode = mapper.valueToTree(bid);
                bidObjectNode.put("wurl", "http://win-url");
                putObjects.add(PutObject.builder().type("json").value(bidObjectNode).build());
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
