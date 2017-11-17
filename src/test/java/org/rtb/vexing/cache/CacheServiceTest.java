package org.rtb.vexing.cache;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.cache.model.request.BidCacheRequest;
import org.rtb.vexing.cache.model.request.Value;
import org.rtb.vexing.cache.model.response.BidCacheResponse;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.model.response.Bid;

import java.io.IOException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

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
        given(httpClient.post((anyInt()), anyString(), anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        given(config.getString("prebid_cache_url")).willReturn("http://cache-service-host");

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
        given(config.getString("prebid_cache_url")).willReturn("invalid_url");

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
        BidCacheResponse result = cacheService.saveBids(emptyList()).result();

        // then
        verifyZeroInteractions(httpClient);
        assertThat(result).isEqualTo(BidCacheResponse.builder().build());
    }

    @Test
    public void shouldRequestToCacheServiceWithGivenParams() throws IOException {
        // when
        cacheService.saveBids(asList(
                Bid.builder().adm("adm1").nurl("nurl1").height(100).width(200).build(),
                Bid.builder().adm("adm2").nurl("nurl2").height(300).width(400).build()
        ));

        // then
        BidCacheRequest bidCacheRequest = captureBidCacheRequest();

        assertThat(bidCacheRequest.puts).isNotNull();
        assertThat(bidCacheRequest.puts).isNotEmpty();
        assertThat(bidCacheRequest.puts.size()).isEqualTo(2);

        Value value1 = bidCacheRequest.puts.get(0).value;
        assertThat(value1).isNotNull();
        assertThat(value1.adm).isEqualTo("adm1");
        assertThat(value1.nurl).isEqualTo("nurl1");
        assertThat(value1.height).isEqualTo(100);
        assertThat(value1.width).isEqualTo(200);

        Value value2 = bidCacheRequest.puts.get(1).value;
        assertThat(value2).isNotNull();
        assertThat(value2.adm).isEqualTo("adm2");
        assertThat(value2.nurl).isEqualTo("nurl2");
        assertThat(value2.height).isEqualTo(300);
        assertThat(value2.width).isEqualTo(400);
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(captor.capture());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }
}
