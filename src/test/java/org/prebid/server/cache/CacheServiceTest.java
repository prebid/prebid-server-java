package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
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

import java.io.IOException;
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
    private HttpClient httpClient;

    private CacheService cacheService;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
        expiredTimeout = timeoutFactory.create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);

        cacheService = new CacheService(httpClient, "http://cache-service/cache",
                "http://cache-service-host/cache?uuid=%PBS_CACHE_UUID%");
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(() -> new CacheService(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new CacheService(httpClient, null, null));
        assertThatNullPointerException().isThrownBy(() -> new CacheService(httpClient, "url", null));
    }

    @Test
    public void getCacheEndpointUrlShouldFailOnInvalidCacheServiceUrl() {
        // then
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCacheEndpointUrl("http", "{invalid:host}"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCacheEndpointUrl("invalid-schema", "example-server:80808"));
    }

    @Test
    public void getCacheEndpointUrlShouldReturnValidUrl() {
        // when
        final String result = CacheService.getCacheEndpointUrl("http", "example.com");

        // then
        assertThat(result).isEqualTo("http://example.com/cache");
    }

    @Test
    public void getCachedAssetUrlTemplateShouldFailOnInvalidCacheServiceUrl() {
        // then
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCachedAssetUrlTemplate("http", "{invalid:host}", "qs"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                CacheService.getCachedAssetUrlTemplate("invalid-schema", "example-server:80808", "qs"));
    }

    @Test
    public void getCachedAssetUrlTemplateShouldReturnValidUrl() {
        // when
        final String result = CacheService.getCachedAssetUrlTemplate("http", "example.com", "qs");

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
        givenHttpClientResponse(200);

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
                .hasMessage("HTTP status code 503, body: response");
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
    public void cacheBidsShouldMakeHttpRequestUsingConfigurationParams() {
        // given
        givenHttpClientResponse(200);

        cacheService = new CacheService(httpClient, "https://cache-service-host:8888/cache",
                "https://cache-service-host:8080/cache?uuid=%PBS_CACHE_UUID%");
        // when
        cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(httpClient).post(eq("https://cache-service-host:8888/cache"), any(), any(), anyLong());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedBody() throws Exception {
        // given
        givenHttpClientResponse(200);

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
        givenHttpClientResponse(200);

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
        cacheService.cacheBidsOpenrtb(emptyList(), emptyList(), null, null, timeout);

        // then
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void cacheBidsOpenrtbShouldSendCacheRequestWithExpectedTtl() throws IOException {
        // given
        givenHttpClientResponse(200);

        // when
        cacheService.cacheBidsOpenrtb(singletonList(givenBidOpenrtb(identity())),
                singletonList(givenBidOpenrtb(identity())), 10, 20, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .extracting(PutObject::getExpiry)
                .containsOnly(10, 20);
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedBody() throws IOException {
        // given
        givenHttpClientResponse(200);

        // when
        final com.iab.openrtb.response.Bid bid1 = givenBidOpenrtb(builder -> builder.impid("impId1"));
        final com.iab.openrtb.response.Bid bid2 = givenBidOpenrtb(builder -> builder.adm("adm1"));
        cacheService.cacheBidsOpenrtb(singletonList(bid1), singletonList(bid2), null, null, timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();
        assertThat(bidCacheRequest.getPuts()).hasSize(2)
                .containsOnly(
                        PutObject.of("json", mapper.valueToTree(bid1), null),
                        PutObject.of("xml", new TextNode(bid2.getAdm()), null));
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBids() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(identity());
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                singletonList(bid), emptyList(), 60, null, timeout);

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
        final com.iab.openrtb.response.Bid bid = givenBidOpenrtb(identity());
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                emptyList(), singletonList(bid), null, 60, timeout);

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
        final Future<Map<com.iab.openrtb.response.Bid, CacheIdInfo>> future = cacheService.cacheBidsOpenrtb(
                asList(bid1, bid2), asList(bid1, bid2), null, null, timeout);

        // then
        final Map<com.iab.openrtb.response.Bid, CacheIdInfo> result = future.result();
        assertThat(result).hasSize(2).containsOnly(
                entry(bid1, CacheIdInfo.of("uuid1", "videoUuid1")),
                entry(bid2, CacheIdInfo.of("uuid2", "videoUuid2")));
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

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);

        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
    }

    private HttpClientResponse givenHttpClientResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(statusCode);

        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(anyString(), any(), captor.capture(), anyLong());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }
}
