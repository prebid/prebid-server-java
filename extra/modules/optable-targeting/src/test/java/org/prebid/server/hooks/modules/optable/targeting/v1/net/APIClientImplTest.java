package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class APIClientImplTest extends BaseOptableTest {

    @Mock
    private HttpClient httpClient;

    private final JacksonMapper jacksonMapper = new JacksonMapper(mapper);

    private APIClient target;

    @Mock
    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        target = new APIClientImpl("http://endpoint.optable.com", httpClient, jacksonMapper, 100);
    }

    @Test
    public void shouldReturnTargetingResult() {
        //  given
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenSuccessHttpResponse("targeting_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                List.of("8.8.8.8"),
                timeout);

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
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse("error_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                List.of("8.8.8.8"),
                timeout);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenEndpointRespondsWithWrongData() {
        //  given
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenSuccessHttpResponse("plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                List.of("8.8.8.8"),
                timeout);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenHttpClientIsCrashed() {
        //  given
        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.failedFuture(new NullPointerException()));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                List.of("8.8.8.8"),
                timeout);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenInternalErrorOccurs() {
        //  given
        when(httpClient.get(any(), any(), anyLong())).thenReturn(Future.succeededFuture(
                givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                List.of("8.8.8.8"),
                timeout);

        // then
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldUseAuthorizationHeaderIfApiKeyIsPresent() {
        //  given
        target = new APIClientImpl("http://endpoint.optable.com", httpClient, jacksonMapper, 10);

        when(httpClient.get(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(givenOptableTargetingProperties(false),
                givenQuery(), List.of("8.8.8.8"), timeout);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).get(any(), headersCaptor.capture(), anyLong());
        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER)).isEqualTo("application/json");
        assertThat(headersCaptor.getValue().get(HttpUtil.AUTHORIZATION_HEADER)).isEqualTo("Bearer key");
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotUseAuthorizationHeaderIfApiKeyIsAbsent() {
        //  given
        when(httpClient.get(any(), any(), anyLong())).thenReturn(Future.succeededFuture(
                givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(null, false),
                givenQuery(),
                List.of("8.8.8.8"),
                timeout);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).get(any(), headersCaptor.capture(), anyLong());
        assertThat(headersCaptor.getValue().get(HttpUtil.ACCEPT_HEADER)).isEqualTo("application/json");
        assertThat(headersCaptor.getValue().get(HttpUtil.AUTHORIZATION_HEADER)).isNull();
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldPassThroughIpAddresses() {
        //  given
        when(httpClient.get(any(), any(), anyLong())).thenReturn(Future.succeededFuture(
                givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                List.of("8.8.8.8", "2001:4860:4860::8888"),
                timeout);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).get(any(), headersCaptor.capture(), anyLong());
        assertThat(headersCaptor.getValue().getAll(HttpUtil.X_FORWARDED_FOR_HEADER))
                .contains("8.8.8.8", "2001:4860:4860::8888");
        assertThat(result.result()).isNull();
    }

    @Test
    public void shouldNotPassThroughIpAddressWhenNotSpecified() {
        //  given
        when(httpClient.get(any(), any(), anyLong())).thenReturn(Future.succeededFuture(
                givenFailHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "plain_text_response.json")));

        // when
        final Future<TargetingResult> result = target.getTargeting(
                givenOptableTargetingProperties(false),
                givenQuery(),
                null,
                timeout);

        // then
        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).get(any(), headersCaptor.capture(), anyLong());
        assertThat(headersCaptor.getValue().get(HttpUtil.X_FORWARDED_FOR_HEADER)).isNull();
        assertThat(result.result()).isNull();
    }
}
