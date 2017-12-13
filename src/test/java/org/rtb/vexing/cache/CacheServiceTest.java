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
import org.rtb.vexing.cache.model.request.BidCacheRequest;
import org.rtb.vexing.cache.model.request.PutValue;
import org.rtb.vexing.cache.model.response.BidCacheResponse;
import org.rtb.vexing.cache.model.response.CacheObject;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.response.Bid;

import java.io.IOException;
import java.util.List;

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
    @Mock
    private ApplicationConfig config;

    private CacheService cacheService;

    @Before
    public void setUp() {
        given(httpClient.postAbs(anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        given(config.getString("cache.scheme")).willReturn("http");
        given(config.getString("cache.host")).willReturn("cache-service-host");
        given(config.getString("cache.query")).willReturn("uuid=%PBS_CACHE_UUID%");

        cacheService = CacheService.create(httpClient, config);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(() -> CacheService.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> CacheService.create(httpClient, null));
    }

    @Test
    public void creationShouldFailOnInvalidCacheServiceUrl() {
        // given
        given(config.getString("cache.scheme")).willReturn("invalid_scheme");

        // then
        assertThatIllegalArgumentException().isThrownBy(() -> CacheService.create(httpClient, config));
    }

    @Test
    public void shouldFailIfBidsAreNull() {
        // then
        assertThatNullPointerException().isThrownBy(() -> cacheService.saveBids(null));
    }

    @Test
    public void shouldReturnEmptyResponseIfBidsAreEmpty() {
        // when
        final List<BidCacheResult> result = cacheService.saveBids(emptyList()).result();

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void shouldRequestToCacheServiceWithExpectedParams() throws IOException {
        // when
        cacheService.saveBids(asList(
                Bid.builder().adm("adm1").nurl("nurl1").height(100).width(200).build(),
                Bid.builder().adm("adm2").nurl("nurl2").height(300).width(400).build()
        ));

        // then
        final BidCacheRequest bidCacheRequest = captureBidCacheRequest();

        assertThat(bidCacheRequest.puts).isNotNull();
        assertThat(bidCacheRequest.puts).isNotEmpty();
        assertThat(bidCacheRequest.puts.size()).isEqualTo(2);

        final PutValue putValue1 = bidCacheRequest.puts.get(0).value;
        assertThat(putValue1).isNotNull();
        assertThat(putValue1.adm).isEqualTo("adm1");
        assertThat(putValue1.nurl).isEqualTo("nurl1");
        assertThat(putValue1.height).isEqualTo(100);
        assertThat(putValue1.width).isEqualTo(200);

        final PutValue putValue2 = bidCacheRequest.puts.get(1).value;
        assertThat(putValue2).isNotNull();
        assertThat(putValue2.adm).isEqualTo("adm2");
        assertThat(putValue2.nurl).isEqualTo("nurl2");
        assertThat(putValue2.height).isEqualTo(300);
        assertThat(putValue2.width).isEqualTo(400);
    }

    @Test
    public void shouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> bidCacheResultFuture = cacheService.saveBids(singleEmptyBid());

        // then
        assertThat(bidCacheResultFuture.failed()).isTrue();
        assertThat(bidCacheResultFuture.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
    }

    @Test
    public void shouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> bidCacheResultFuture = cacheService.saveBids(singleEmptyBid());

        // then
        assertThat(bidCacheResultFuture.failed()).isTrue();
        assertThat(bidCacheResultFuture.cause()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
    }

    @Test
    public void shouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> bidCacheResultFuture = cacheService.saveBids(singleEmptyBid());

        // then
        assertThat(bidCacheResultFuture.failed()).isTrue();
        assertThat(bidCacheResultFuture.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("HTTP status code 503");
    }

    @Test
    public void shouldFailIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<?> bidCacheResultFuture = cacheService.saveBids(singleEmptyBid());

        // then
        assertThat(bidCacheResultFuture.failed()).isTrue();
        assertThat(bidCacheResultFuture.cause()).isInstanceOf(DecodeException.class);
    }

    @Test
    public void shouldFailIfCacheEntriesNumberDoesNotMatchBidsNumber() {
        // given
        givenHttpClientReturnsResponse(200, "{}");

        // when
        final Future<?> bidCacheResultFuture = cacheService.saveBids(singleEmptyBid());

        // then
        assertThat(bidCacheResultFuture.failed()).isTrue();
        assertThat(bidCacheResultFuture.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Put response length didn't match");
    }

    @Test
    public void shouldReturnCacheResult() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(BidCacheResponse.builder()
                .responses(singletonList(CacheObject.builder().uuid("uuid1").build()))
                .build()));

        // when
        final Future<List<BidCacheResult>> bidCacheResultFuture = cacheService.saveBids(singleEmptyBid());

        // then
        final List<BidCacheResult> bidCacheResults = bidCacheResultFuture.result();
        assertThat(bidCacheResults).hasSize(1)
                .containsOnly(BidCacheResult.builder()
                        .cacheId("uuid1")
                        .cacheUrl("http://cache-service-host/cache?uuid=uuid1")
                        .build());
    }

    @Test
    public void shouldMakeHttpRequestUsingConfigurationParams() {
        // given
        given(config.getString("cache.scheme")).willReturn("https");
        given(config.getString("cache.host")).willReturn("cache-service-host:8888");
        cacheService = CacheService.create(httpClient, config);

        // when
        cacheService.saveBids(singleEmptyBid());

        // then
        verify(httpClient).postAbs(eq("https://cache-service-host:8888/cache"), any());
    }

    private static List<Bid> singleEmptyBid() {
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

    @Test
    public void shouldFailCachedAssetURLIfUUIDIsMissing() {
        // then
        assertThatNullPointerException().isThrownBy(() -> cacheService.getCachedAssetURL(null));
    }

    @Test
    public void shouldReturnCachedAssetURLForUUID() {
        // when
        final String cachedAssetURL = cacheService.getCachedAssetURL("uuid1");

        // then
        assertThat(cachedAssetURL).isEqualTo("http://cache-service-host/cache?uuid=uuid1");
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(captor.capture());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }
}
