package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class APIClientTest extends BaseOptableTest {

    private static final Double LOG_SAMPLING_RATE = 100.0;

    private static final String ACCEPT_HEADER = "Accept";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String X_FORWARDED_FOR_HEADER = "X-forwarded-for";

    @Mock
    private HttpClient httpClient;

    private APIClient target;

    private final OptableResponseMapper parser = new OptableResponseMapper(
            new JacksonMapper(ObjectMapperProvider.mapper()));

    @BeforeEach
    public void setUp() {
        target = new APIClient("endpoint",
                httpClient,
                LOG_SAMPLING_RATE,
                parser,
                null);
    }

    @Test
    public void shouldReturnTargetingResult() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenSuccessHttpResponse("targeting_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        assertThat(result.result()).isNotNull();
        final TargetingResult res = result.result();
        final User user = res.getOrtb2().getUser();
        assertThat(user.getEids().getFirst().getUids().getFirst().getId()).isEqualTo("uid_id1");
        assertThat(user.getData().getFirst().getSegment().getFirst().getId()).isEqualTo("segment_id");
    }

    @Test
    public void shouldReturnNullWhenEndpointRespondsWithError() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse("error_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenEndpointRespondsWithWrongData() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenSuccessHttpResponse("plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenHttpClientIsCrashed() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenThrow(new NullPointerException());

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenInternalErrorOccurs() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldUseAuthorizationHeaderIfApiKeyIsPresent() {
        //  given
        target = new APIClient("endpoint",
                httpClient,
                LOG_SAMPLING_RATE,
                parser,
                "key");

        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient)
                .request(eq(HttpMethod.GET), any(), headersCaptor.capture(), nullable(String.class), anyLong());
        assertThat(headersCaptor.getValue().get(ACCEPT_HEADER)).isEqualTo("application/json");
        assertThat(headersCaptor.getValue().get(AUTHORIZATION_HEADER)).isEqualTo("Bearer key");
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotUseAuthorizationHeaderIfApiKeyIsAbsent() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", List.of("8.8.8.8"), 1000);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient)
                .request(eq(HttpMethod.GET), any(), headersCaptor.capture(), nullable(String.class), anyLong());
        assertThat(headersCaptor.getValue().get(ACCEPT_HEADER)).isEqualTo("application/json");
        assertThat(headersCaptor.getValue().get(AUTHORIZATION_HEADER)).isNull();
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldPassThroughIpAddresses() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                "query",
                List.of("8.8.8.8", "2001:4860:4860::8888"),
                1000);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient)
                .request(eq(HttpMethod.GET), any(), headersCaptor.capture(), nullable(String.class), anyLong());
        assertThat(headersCaptor.getValue().getAll(X_FORWARDED_FOR_HEADER)).contains("8.8.8.8", "2001:4860:4860::8888");
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotPassThroughIpAddressWhenNotSpecified() {
        //  given
        when(httpClient.request(eq(HttpMethod.GET), any(), any(), nullable(String.class), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting("query", null, 1000);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient)
                .request(eq(HttpMethod.GET), any(), headersCaptor.capture(), nullable(String.class), anyLong());
        assertThat(headersCaptor.getValue().get(X_FORWARDED_FOR_HEADER)).isNull();
        assertThat(result.result()).isNull();
    }
}
