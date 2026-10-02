package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.Cache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CachedAPIClientTest extends BaseOptableTest {

    @Mock
    private APIClientImpl apiClient;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Cache cache;

    private CachedAPIClient target;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        target = new CachedAPIClient(apiClient, cache, false);
        when(timeout.remaining()).thenReturn(1000L);
    }

    @Test
    public void shouldCallAPIAndAddTargetingResultsToCache() {
        // given
        when(cache.get(any())).thenReturn(Future.failedFuture("error"));
        when(cache.put(any(), any(), anyInt())).thenReturn(Future.succeededFuture());
        final Query query = givenQuery();
        when(apiClient.getTargeting(any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(cache).put(any(), eq(targetingResult.result()), anyInt());
    }

    @Test
    public void shouldCallAPIAndAddTargetingResultsToCacheWhenCacheReturnsFailure() {
        // given
        when(cache.get(any())).thenReturn(Future.failedFuture(new IllegalArgumentException("message")));
        when(cache.put(any(), any(), anyInt())).thenReturn(Future.succeededFuture());
        final Query query = givenQuery();
        when(apiClient.getTargeting(any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(apiClient, times(1)).getTargeting(any(), any(), any(), any(), any());
        verify(cache).put(any(), eq(targetingResult.result()), anyInt());
    }

    @Test
    public void shouldUseCachedResult() {
        // given
        when(cache.get(any())).thenReturn(Future.succeededFuture(givenTargetingResult()));
        final Query query = givenQuery();

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(cache, times(1)).get(any());
        verify(apiClient, times(0)).getTargeting(any(), any(), any(), any(), any());
        verify(cache, times(0)).put(any(), eq(targetingResult.result()), anyInt());
    }

    @Test
    public void shouldNotFailWhenApiClientIsFailed() {
        // given
        final Query query = givenQuery();
        when(cache.get(any())).thenReturn(Future.failedFuture("empty"));
        when(apiClient.getTargeting(any(), any(), any(), any(), any()))
                .thenReturn(Future.failedFuture(new NullPointerException()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        assertThat(targetingResult.result()).isNull();
        verify(cache, times(0)).put(any(), eq(targetingResult.result()), anyInt());
    }

    @Test
    public void shouldCacheEmptyResultWhenCircuitBreakerIsOn() {
        // given
        final Query query = givenQuery();
        when(cache.get(any())).thenReturn(Future.failedFuture("empty"));
        when(apiClient.getTargeting(any(), any(), any(), any(), any()))
                .thenReturn(Future.failedFuture(new NullPointerException()));
        when(cache.put(any(), any(), anyInt())).thenReturn(Future.succeededFuture());

        // when
        target = new CachedAPIClient(apiClient, cache, true);
        final Future<TargetingResult> targetingResult = target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final TargetingResult result = targetingResult.result();
        assertThat(result).isNotNull();
        assertThat(result.getOrtb2()).isNull();
        assertThat(result.getAudience()).isNull();
        verify(cache, times(1)).put(any(), eq(targetingResult.result()), anyInt());
    }

    @Test
    public void shouldIncludeHidInCacheKey() {
        // given
        final Query query = Query.of("&id=e%3Aemail", "&hid=c:234", "&gdpr=1", "");
        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(cache.get(keyCaptor.capture())).thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final String key = keyCaptor.getValue();
        assertThat(key).contains(URLEncoder.encode("&hid=c:234", StandardCharsets.UTF_8));
    }

    @Test
    public void shouldIncludeHidAttributesInCacheKey() {
        // given
        final Query query = Query.of("&id=e%3Aemail", "", "&gdpr=1", "&bundle=com.example.app");
        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(cache.get(keyCaptor.capture())).thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final String key = keyCaptor.getValue();
        assertThat(key).contains(URLEncoder.encode("&bundle=com.example.app", StandardCharsets.UTF_8));
    }

    @Test
    public void shouldBuildCacheKeyWithAllComponents() {
        // given
        final Query query = Query.of("&id=e%3Aemail", "&hid=c:234", "&gdpr=1", "&id5_signature=sig");
        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(cache.get(keyCaptor.capture())).thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        target.getTargeting(
                givenOptableTargetingProperties("key", "accountId", "origin", true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final String key = keyCaptor.getValue();
        assertThat(key).isEqualTo(
                "accountId:origin:8.8.8.8:"
                        + URLEncoder.encode("&id=e%3Aemail", StandardCharsets.UTF_8)
                        + ":"
                        + URLEncoder.encode("&hid=c:234", StandardCharsets.UTF_8)
                        + ":"
                        + URLEncoder.encode("&id5_signature=sig", StandardCharsets.UTF_8));
    }

    @Test
    public void shouldUseNullInCacheKeyWhenHidIsNull() {
        // given
        final Query query = Query.of("&id=e%3Aemail", null, "&gdpr=1", null);
        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(cache.get(keyCaptor.capture())).thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        target.getTargeting(
                givenOptableTargetingProperties(true),
                query,
                List.of("8.8.8.8"),
                "user agent",
                timeout);

        // then
        final String key = keyCaptor.getValue();
        assertThat(key).endsWith(":null:null");
    }
}
