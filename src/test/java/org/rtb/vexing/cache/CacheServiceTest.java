package org.rtb.vexing.cache;

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
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.cache.model.request.BannerValue;
import org.rtb.vexing.cache.model.request.BidCacheRequest;
import org.rtb.vexing.cache.model.request.PutObject;
import org.rtb.vexing.cache.model.response.BidCacheResponse;
import org.rtb.vexing.cache.model.response.CacheObject;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.response.Bid;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;
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

    @Before
    public void setUp() {
        given(httpClient.postAbs(anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

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

        //then
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

        //then
        assertThat(result).isEqualTo("http://example.com/cache?qs");
    }

    @Test
    public void getCachedAssetURLShouldFailIfUUIDIsMissing() {
        // then
        assertThatNullPointerException().isThrownBy(() -> cacheService.getCachedAssetURL(null));
    }

    @Test
    public void getCachedAssetURLShouldReturnExpectedValue() {
        // when
        final String cachedAssetURL = cacheService.getCachedAssetURL("uuid1");

        // then
        assertThat(cachedAssetURL).isEqualTo("http://cache-service-host/cache?uuid=uuid1");
    }

    @Test
    public void shouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(() -> cacheService.cacheBids(null, null));
        assertThatNullPointerException().isThrownBy(() -> cacheService.cacheBids(emptyList(), null));
        assertThatNullPointerException().isThrownBy(() -> cacheService.cacheBidsOpenrtb(null, null));
        assertThatNullPointerException().isThrownBy(() -> cacheService.cacheBidsOpenrtb(emptyList(), null));
    }

    @Test
    public void cacheBidsShouldReturnEmptyResponseIfBidsAreEmpty() {
        // when
        final List<BidCacheResult> result = cacheService.cacheBids(emptyList(), timeout()).result();

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void cacheBidsShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBids(singleBidList(), GlobalTimeout.create(1000));

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(httpClientRequest).setTimeout(timeoutCaptor.capture());
        assertThat(timeoutCaptor.getValue()).isCloseTo(1000L, offset(20L));
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
                timeout());

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();

        assertThat(bidCacheRequest.puts).isNotNull();
        assertThat(bidCacheRequest.puts).isNotEmpty();
        assertThat(bidCacheRequest.puts.size()).isEqualTo(4);

        final PutObject putObject1 = bidCacheRequest.puts.get(0);
        final BannerValue putValue1 = mapper.treeToValue(putObject1.value, BannerValue.class);
        assertThat(putObject1).isNotNull();

        assertThat(putValue1.adm).isEqualTo("adm1");
        assertThat(putValue1.nurl).isEqualTo("nurl1");
        assertThat(putValue1.height).isEqualTo(100);
        assertThat(putValue1.width).isEqualTo(200);
        assertThat(putValue1.width).isEqualTo(200);
        assertThat(putObject1.type).isEqualTo("json");

        final PutObject putObject2 = bidCacheRequest.puts.get(1);
        final BannerValue putValue2 = mapper.treeToValue(putObject2.value, BannerValue.class);
        assertThat(putValue2).isNotNull();
        assertThat(putValue2.adm).isEqualTo("adm2");
        assertThat(putValue2.nurl).isEqualTo("nurl2");
        assertThat(putValue2.height).isEqualTo(300);
        assertThat(putValue2.width).isEqualTo(400);
        assertThat(putObject2.type).isEqualTo("json");

        final PutObject putObject3 = bidCacheRequest.puts.get(2);
        assertThat(putObject3).isNotNull();
        assertThat(putObject3.value.asText()).isEqualTo(adm3);
        assertThat(putObject3.type).isEqualTo("xml");

        final PutObject putObject4 = bidCacheRequest.puts.get(3);
        assertThat(putObject4).isNotNull();
        assertThat(putObject4.value.asText()).isEqualTo(adm4);
        assertThat(putObject4.type).isEqualTo("xml");
    }

    @Test
    public void cacheBidsShouldFailIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(),
                GlobalTimeout.create(Clock.systemDefaultZone().millis() - 10000, 1000));

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
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
    }

    @Test
    public void cacheBidsShouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
    }

    @Test
    public void cacheBidsShouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout());

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
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(DecodeException.class);
    }

    @Test
    public void cacheBidsShouldFailIfCacheEntriesNumberDoesNotMatchBidsNumber() {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        // when
        final Future<?> future = cacheService.cacheBids(singleBidList(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Put response length didn't match");
    }

    @Test
    public void cacheBidsShouldReturnCacheResult() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(BidCacheResponse.builder()
                .responses(singletonList(CacheObject.builder().uuid("uuid1").build()))
                .build()));

        // when
        final Future<List<BidCacheResult>> future = cacheService.cacheBids(singleBidList(), timeout());

        // then
        final List<BidCacheResult> bidCacheResults = future.result();
        assertThat(bidCacheResults).hasSize(1)
                .containsOnly(BidCacheResult.builder()
                        .cacheId("uuid1")
                        .cacheUrl("http://cache-service-host/cache?uuid=uuid1")
                        .build());
    }

    @Test
    public void cacheBidsShouldMakeHttpRequestUsingConfigurationParams() {
        // given
        cacheService = new CacheService(httpClient, "https://cache-service-host:8888/cache",
                "https://cache-service-host:8080/cache?uuid=%PBS_CACHE_UUID%");
        // when
        cacheService.cacheBids(singleBidList(), timeout());

        // then
        verify(httpClient).postAbs(eq("https://cache-service-host:8888/cache"), any());
    }

    @Test
    public void cacheBidsShouldReturnEmptyResultIfBidsAreEmpty() {
        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(emptyList(), timeout());

        // then
        verifyZeroInteractions(httpClient);
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(emptyList());
    }

    @Test
    public void cacheBidsOpenrtbShouldPerformHttpRequestWithExpectedTimeout() {
        // when
        cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), GlobalTimeout.create(1000));

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(httpClientRequest).setTimeout(timeoutCaptor.capture());
        // offset value is greater here since cacheBidsOpenrtb performs mapper.valueToTree and this call might take a
        // little more time if mapper's caches hasn't been warmed up (this might happen if the test is the first one
        // that triggers mapper activity with affected classes)
        assertThat(timeoutCaptor.getValue()).isCloseTo(1000L, offset(200L));
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(),
                GlobalTimeout.create(Clock.systemDefaultZone().millis() - 10000, 1000));

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
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
    }

    @Test
    public void cacheBidsOpenrtbShouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout());

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
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(DecodeException.class);
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnEmptyResultIfCacheEntriesNumberDoesNotMatchBidsNumber() {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        // when
        final Future<?> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Put response length didn't match");
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnCacheResult() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(BidCacheResponse.builder()
                .responses(singletonList(CacheObject.builder().uuid("uuid1").build()))
                .build()));

        // when
        final Future<List<String>> future = cacheService.cacheBidsOpenrtb(singleBidListOpenrtb(), timeout());

        // then
        final List<String> result = future.result();
        assertThat(result).hasSize(1)
                .containsOnly("uuid1");
    }

    private static GlobalTimeout timeout() {
        return GlobalTimeout.create(500);
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
