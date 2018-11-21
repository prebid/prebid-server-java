package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.cache.account.AccountCacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.cache.proto.request.BannerValue;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

public class CacheServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AccountCacheService accountCacheService;
    @Mock
    private HttpClient httpClient;

    private final CacheTtl mediaTypeCacheTtl = CacheTtl.of(null, null);
    private CacheService cacheService;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() throws MalformedURLException {
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        cacheService = new CacheService(accountCacheService, mediaTypeCacheTtl, httpClient,
                new URL("http://cache-service/cache"),"http://cache-service-host/cache?uuid=%PBS_CACHE_UUID%");
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new CacheService(null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new CacheService(accountCacheService, null, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new CacheService(accountCacheService, mediaTypeCacheTtl, null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new CacheService(accountCacheService, mediaTypeCacheTtl, httpClient, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new CacheService(accountCacheService, mediaTypeCacheTtl, httpClient, new URL("http://validurl.com"), null));
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
        final String result = CacheService.getCachedAssetUrlTemplate("http", "example.com", "cache", "qs").toString();

        // then
        assertThat(result).isEqualTo("http://example.com/cache?qs");
    }

    @Test
    public void getCachedAssetURLShouldReturnExpectedValue() {
        // when
        final String cachedAssetURL = cacheService.getCachedAssetURL("uuid1");

        // then
        assertThat(cachedAssetURL).isEqualTo("http://cache-service-host/cache?uuid=uuid1");
    }

    @Test
    public void cacheBidsShouldReturnEmptyResponseIfBidsAreEmpty() {
        // when
        final List<BidCacheResult> result = cacheService.cacheBids(emptyList(), timeout).result();

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedTimeout() {
        // given
        givenHttpClientReturnsResponse(200, null);

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
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
    }

    @Test
    public void cacheBidsShouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout);

        // then
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
        givenHttpClientReturnsResponse(200, null);

        cacheService = new CacheService(accountCacheService, mediaTypeCacheTtl, httpClient,
                new URL("https://cache-service-host:8888/cache"), "https://cache-service-host:8080/cache?uuid=%PBS_CACHE_UUID%");

        // when
        cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(httpClient).post(eq("https://cache-service-host:8888/cache"), any(), any(), anyLong());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedBody() throws Exception {
        // given
        givenHttpClientReturnsResponse(200, null);

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
                        PutObject.of("json", mapper.valueToTree(BannerValue.of("adm1", "nurl1", 200, 100)), null),
                        PutObject.of("json", mapper.valueToTree(BannerValue.of("adm2", "nurl2", 400, 300)), null),
                        PutObject.of("xml", new TextNode(adm3), null),
                        PutObject.of("xml", new TextNode(adm4), null)
                );
    }

    @Test
    public void cacheBidsOpenrtbShouldWrapEmtpyAdMFieldUsingNurlFieldValue() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1").adm("adm1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId1").nurl("adm2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), asList(imp1),
                CacheContext.of(true, null, true, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(4)
                .containsOnly(
                        PutObject.of("json", mapper.valueToTree(bid1), null),
                        PutObject.of("json", mapper.valueToTree(bid2), null),
                        PutObject.of("xml", new TextNode("adm1"), null),
                        PutObject.of("xml", new TextNode("<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>" +
                                "prebid.org wrapper</AdSystem><VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI><Impression>" +
                                "</Impression><Creatives></Creatives></Wrapper></Ad></VAST>"), null)
                );
    }

    @Test
    public void cacheBidsShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
        final Future<List<BidCacheResult>> future = cacheService.cacheBids(singleBidList(), timeout);

        // then
        final List<BidCacheResult> bidCacheResults = future.result();
        assertThat(bidCacheResults).hasSize(1)
                .containsOnly(BidCacheResult.of("uuid1", "http://cache-service-host/cache?uuid=uuid1"));
    }

    @Test
    public void cacheBidsVideoOnlyShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        cacheService.cacheBidsVideoOnly(asList(
                givenBid(builder -> builder.mediaType(MediaType.banner).adm("adm1")),
                givenBid(builder -> builder.mediaType(MediaType.video).adm("adm2"))),
                timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .containsOnly(PutObject.of("xml", new TextNode("adm2"), null));
    }

    @Test
    public void cacheBidsVideoOnlyShouldReturnExpectedResult() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
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
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2").adm("adm2"));
        final Imp imp1 = givenImp(identity());
        final Imp imp2 = givenImp(builder -> builder.id("impId2").video(Video.builder().build()));
        cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), asList(imp1, imp2),
                CacheContext.of(true, null, true, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(3)
                .containsOnly(
                        PutObject.of("json", mapper.valueToTree(bid1), null),
                        PutObject.of("json", mapper.valueToTree(bid2), null),
                        PutObject.of("xml", new TextNode(bid2.getAdm()), null));
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromBid() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(builder -> builder.impid("impId1").exp(10))),
                singletonList(givenImp(buider -> buider.id("impId1").exp(20))),
                CacheContext.of(true, null, false, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromImp() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(buider -> buider.exp(10))),
                CacheContext.of(true, 20, false, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromRequest() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.of(true, 10, false, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);

        verifyZeroInteractions(accountCacheService);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromAccountMediaTypeTtl() throws IOException {
        // given
        cacheService = new CacheService(accountCacheService, CacheTtl.of(20, null), httpClient,
                new URL("http://cache-service/cache"),"http://cache-service-host/cache?uuid=%PBS_CACHE_UUID%");

        givenHttpClientReturnsResponse(200, null);

        // when
        given(accountCacheService.getCacheTtlByAccountId(any(), any()))
                .willReturn(Future.succeededFuture(CacheTtl.of(10, null)));

        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.of(true, null, false, null), "publisher", timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtlFromMediaTypeTtl() throws IOException {
        // given
        cacheService = new CacheService(accountCacheService, CacheTtl.of(10, null), httpClient,
                new URL("http://cache-service/cache"),"http://cache-service-host/cache?uuid=%PBS_CACHE_UUID%");

        givenHttpClientReturnsResponse(200, null);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.of(true, null, false, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsOnly(10);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithNoTtl() throws IOException {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        cacheService.cacheBidsOpenrtb(
                singletonList(givenBidOpenrtb(identity())), singletonList(givenImp(identity())),
                CacheContext.of(true, null, false, null), null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(1)
                .extracting(PutObject::getExpiry)
                .containsNull();
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(identity());
        final Imp imp = givenImp(identity());
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(imp),
                CacheContext.of(true, null, false, null), null, timeout);

        // then
        final Map<com.iab.openrtb.response.Bid, CacheIdInfo> result = future.result();
        assertThat(result).hasSize(1)
                .containsEntry(bid, CacheIdInfo.of("uuid1", null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForVideoBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final Imp imp = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), singletonList(imp),
                CacheContext.of(false, null, true, null), null, timeout);

        // then
        final Map<com.iab.openrtb.response.Bid, CacheIdInfo> result = future.result();
        assertThat(result).hasSize(1)
                .containsEntry(bid, CacheIdInfo.of(null, "uuid1"));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(asList(CacheObject.of("uuid1"), CacheObject.of("uuid2"),
                        CacheObject.of("videoUuid1"), CacheObject.of("videoUuid2")))));

        // when
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id("impId2").video(Video.builder().build()));

        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), asList(imp1, imp2),
                CacheContext.of(true, null, true, null), null, timeout);

        // then
        final Map<com.iab.openrtb.response.Bid, CacheIdInfo> result = future.result();
        assertThat(result).hasSize(2).containsOnly(
                entry(bid1, CacheIdInfo.of("uuid1", "videoUuid1")),
                entry(bid2, CacheIdInfo.of("uuid2", "videoUuid2")));
    }

    @Test
    public void cacheBidsOpenrtbShouldNotCacheVideoBidWithMissingImpId() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.impid("impId2"));
        final Imp imp1 = givenImp(builder -> builder.id("impId1").video(Video.builder().build()));
        final Imp imp2 = givenImp(builder -> builder.id(null).video(Video.builder().build()));
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), asList(imp1, imp2),
                CacheContext.of(false, null, true, null), null, timeout);

        // then
        final Map<com.iab.openrtb.response.Bid, CacheIdInfo> result = future.result();
        assertThat(result).hasSize(1)
                .containsEntry(bid1, CacheIdInfo.of(null, "uuid1"));
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
