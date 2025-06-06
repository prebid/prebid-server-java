package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.vertx.httpclient.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableHttpClientWrapperTest {

    @Mock
    private Vertx vertx;

    @Mock(strictness = LENIENT)
    io.vertx.core.http.HttpClient httpClient;

    @Mock(strictness = LENIENT)
    private HttpClientRequest httpClientRequest;

    @Mock
    private HttpClientResponse httpClientResponse;

    private HttpClientProperties httpClientProperties;

    private OptableHttpClientWrapper target;

    @BeforeEach
    public void setUp() {
        when(httpClient.request(any())).thenReturn(Future.succeededFuture(httpClientRequest));
        given(httpClientRequest.send()).willReturn(Future.succeededFuture(httpClientResponse));
        given(httpClientRequest.send(any(Buffer.class))).willReturn(Future.succeededFuture(httpClientResponse));
        when(vertx.createHttpClient(any(HttpClientOptions.class))).thenReturn(httpClient);
        httpClientProperties = giveHttpClientProperties();
        target = new OptableHttpClientWrapper(vertx, httpClientProperties);
    }

    @Test
    public void shouldCreateHttpClient() {
        // given nad when
        final HttpClient httpClient = target.getHttpClient();

        // then
        assertThat(httpClient).isNotNull();
        final ArgumentCaptor<HttpClientOptions> httpClientOptionsCaptor =
                ArgumentCaptor.forClass(HttpClientOptions.class);
        verify(vertx).createHttpClient(httpClientOptionsCaptor.capture());

        assertThat(httpClientOptionsCaptor.getValue()).satisfies(options -> {
            assertThat(options.getMaxPoolSize()).isEqualTo(8);
            assertThat(options.isKeepAlive()).isEqualTo(true);
            assertThat(options.getKeepAliveTimeout()).isEqualTo(60);
            assertThat(options.getHttp2KeepAliveTimeout()).isEqualTo(60);
            assertThat(options.isTryUseCompression()).isEqualTo(true);
            assertThat(options.isSsl()).isEqualTo(true);
        });
    }

    @Test
    public void requestShouldPerformHttpRequestWithExpectedParams() {
        // given and when
        target.getHttpClient().request(HttpMethod.POST, "http://www.example.com",
                MultiMap.caseInsensitiveMultiMap(), "body", 500L);

        // then
        final ArgumentCaptor<RequestOptions> requestOptionsArgumentCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(httpClient).request(requestOptionsArgumentCaptor.capture());

        final RequestOptions expectedRequestOptions = new RequestOptions()
                .setFollowRedirects(true)
                .setConnectTimeout(500L)
                .setMethod(HttpMethod.POST)
                .setAbsoluteURI("http://www.example.com")
                .setHeaders(MultiMap.caseInsensitiveMultiMap());
        assertThat(requestOptionsArgumentCaptor.getValue().toJson()).isEqualTo(expectedRequestOptions.toJson());

        verify(httpClientRequest).send(eq(Buffer.buffer("body".getBytes())));
    }

    private HttpClientProperties giveHttpClientProperties() {
        final HttpClientProperties properties = new HttpClientProperties();
        properties.setMaxPoolSize(8);
        properties.setConnectTimeoutMs(2000);
        properties.setPoolCleanerPeriodMs(6000);
        properties.setIdleTimeoutMs(200);
        properties.setUseCompression(true);
        properties.setSsl(true);
        properties.setJksPath("/some_path");
        properties.setJksPassword("password");
        properties.setMaxRedirects(2);
        return properties;
    }
}
