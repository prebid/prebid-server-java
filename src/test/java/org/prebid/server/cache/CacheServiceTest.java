package org.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.DecodeException;
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

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    @Mock
    private HttpClientRequest httpClientRequest;

    private CacheService cacheService;

    private Timeout timeout;
    private Timeout expiredTimeout;

    @Before
    public void setUp() {
        given(httpClient.postAbs(anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

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
        assertThatIllegalArgumentException().isThrownBy(() -> CacheService.getCacheEndpointUrl("http",
                "{invalid:host}"));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheService.getCacheEndpointUrl("invalid-schema",
                "example-server:80808"));
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
        assertThatIllegalArgumentException().isThrownBy(() -> CacheService.getCachedAssetUrlTemplate("qs", "http",
                "{invalid:host}"));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheService.getCachedAssetUrlTemplate("qs",
                "invalid-schema",
                "example-server:80808"));
    }

    @Test
    public void getCachedAssetUrlTemplateShouldReturnValidUrl() {
        // when
        final String result = CacheService.getCachedAssetUrlTemplate("qs", "http", "example.com");

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
        // when
        cacheService.cacheBids(singleBidList(), timeout);

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(httpClientRequest).setTimeout(timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isEqualTo(500L);
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedBody() throws Exception {
        // given
        final String adm3 = "<script type=\"application/javascript\" src=\"http://nym1-ib.adnxs"
                + "f3919239&pp=${AUCTION_PRICE}&\"></script>";
        final String adm4 = "<img src=\"https://tpp.hpppf.com/simgad/11261207092432736464\" border=\"0\" "
                + "width=\"184\" height=\"90\" alt=\"\" class=\"img_ad\">";

        // when
        cacheService.cacheBids(
                asList(
                        Bid.builder().adm("adm1").nurl("nurl1").height(100).width(200).build(),
                        Bid.builder().adm("adm2").nurl("nurl2").height(300).width(400).build(),
                        Bid.builder().adm(adm3).mediaType(MediaType.video).build(),
                        Bid.builder().adm(adm4).mediaType(MediaType.video).build()),
                timeout);

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();

        assertThat(bidCacheRequest.getPuts()).hasSize(4);

        final PutObject putObject1 = bidCacheRequest.getPuts().get(0);
        final BannerValue putValue1 = mapper.treeToValue(putObject1.getValue(), BannerValue.class);
        assertThat(putObject1).isNotNull();

        assertThat(putValue1.getAdm()).isEqualTo("adm1");
        assertThat(putValue1.getNurl()).isEqualTo("nurl1");
        assertThat(putValue1.getHeight()).isEqualTo(100);
        assertThat(putValue1.getWidth()).isEqualTo(200);
        assertThat(putObject1.getType()).isEqualTo("json");

        final PutObject putObject2 = bidCacheRequest.getPuts().get(1);
        final BannerValue putValue2 = mapper.treeToValue(putObject2.getValue(), BannerValue.class);
        assertThat(putValue2).isNotNull();
        assertThat(putValue2.getAdm()).isEqualTo("adm2");
        assertThat(putValue2.getNurl()).isEqualTo("nurl2");
        assertThat(putValue2.getHeight()).isEqualTo(300);
        assertThat(putValue2.getWidth()).isEqualTo(400);
        assertThat(putObject2.getType()).isEqualTo("json");

        final PutObject putObject3 = bidCacheRequest.getPuts().get(2);
        assertThat(putObject3).isNotNull();
        assertThat(putObject3.getValue().asText()).isEqualTo(adm3);
        assertThat(putObject3.getType()).isEqualTo("xml");

        final PutObject putObject4 = bidCacheRequest.getPuts().get(3);
        assertThat(putObject4).isNotNull();
        assertThat(putObject4.getValue().asText()).isEqualTo(adm4);
        assertThat(putObject4.getType()).isEqualTo("xml");
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
    public void cacheBidsShouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
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
        assertThat(future.cause()).isInstanceOf(DecodeException.class);
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
                .hasMessage("Put response length didn't match");
    }

    @Test
    public void cacheBidsShouldReturnCacheResult() throws JsonProcessingException {
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
    public void cacheBidsShouldMakeHttpRequestUsingConfigurationParams() {
        // given
        cacheService = new CacheService(httpClient, "https://cache-service-host:8888/cache",
                "https://cache-service-host:8080/cache?uuid=%PBS_CACHE_UUID%");
        // when
        cacheService.cacheBids(singleBidList(), timeout);

        // then
        verify(httpClient).postAbs(eq("https://cache-service-host:8888/cache"), any());
    }

    @Test
    public void cacheBidsShouldReturnEmptyResultIfBidsAreEmpty() {
        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(emptyList(), timeout);

        // then
        verifyZeroInteractions(httpClient);
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(emptyList());
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(httpClientRequest).setTimeout(timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isEqualTo(500L);
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), expiredTimeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class);
        verifyZeroInteractions(httpClient);
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("HTTP status code 503, body: response");
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(DecodeException.class);
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnEmptyResultIfCacheEntriesNumberDoesNotMatchBidsNumber() {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Put response length didn't match");
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnCacheResult() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(
                BidCacheResponse.of(singletonList(CacheObject.of("uuid1")))));

        // when
        final Future<List<String>> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout);

        // then
        final List<String> result = future.result();
        assertThat(result).hasSize(1)
                .containsOnly("uuid1");
    }

    private static List<com.iab.openrtb.response.Bid> singleBidListOpenrtb() {
        return singletonList(com.iab.openrtb.response.Bid.builder().build());
    }

    private static List<Bid> singleBidList() {
        return singletonList(Bid.builder().build());
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
        given(httpClient.postAbs(anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.statusCode()).willReturn(statusCode);
        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> withRequestAndPassResponseToHandler(HttpClientResponse httpClientResponse) {
        return inv -> {
            // invoking passed HttpClientResponse handler right away passing mock response to it
            ((Handler<HttpClientResponse>) inv.getArgument(1)).handle(httpClientResponse);
            return httpClientRequest;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(captor.capture());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }
}
