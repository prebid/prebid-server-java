package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingTest extends BaseOptableTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Cache cache;

    @Mock
    private IdsMapper idsMapper;

    @Mock
    private APIClient apiClient;

    private QueryBuilder queryBuilder = new QueryBuilder("c,c1,email");

    private OptableTargeting target;

    private OptableAttributes optableAttributes;

    @BeforeEach
    public void setUp() {
        when(cache.isEnabled()).thenReturn(false);
        optableAttributes = givenOptableAttributes();
        target = new OptableTargeting(cache, idsMapper, queryBuilder, apiClient);
    }

    @Test
    public void shouldReturnTargetingResultsAndNotUseCache() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(apiClient.getTargeting(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(cache, times(0)).get(any());
        verify(apiClient, times(1)).getTargeting(any(), any(), anyLong());
        verify(cache, times(0)).put(any(), eq(targetingResult.result()));
    }

    @Test
    public void shouldCallAPIAndAddTargetingResultsToCache() {
        // given
        when(cache.isEnabled()).thenReturn(true);
        when(cache.get(any())).thenReturn(Future.succeededFuture(null));
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(apiClient.getTargeting(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(cache).put(any(), eq(targetingResult.result()));
    }

    @Test
    public void shouldCallAPIAndAddTargetingResultsToCacheWhenCacheReturnsFailure() {
        // given
        when(cache.isEnabled()).thenReturn(true);
        when(cache.get(any())).thenReturn(Future.failedFuture(new IllegalArgumentException("message")));
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(apiClient.getTargeting(any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(apiClient, times(1)).getTargeting(any(), any(), anyLong());
        verify(cache).put(any(), eq(targetingResult.result()));
    }

    @Test
    public void shouldUseCachedResult() {
        // given
        when(cache.isEnabled()).thenReturn(true);
        when(cache.get(any())).thenReturn(Future.succeededFuture(givenTargetingResult()));
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of(Id.of(Id.ID5, "id")));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        final User user = targetingResult.result().getOrtb2().getUser();
        assertThat(user).isNotNull()
                .returns("source", it -> it.getEids().getFirst().getSource())
                .returns("id", it -> it.getEids().getFirst().getUids().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getId())
                .returns("id", it -> it.getData().getFirst().getSegment().getFirst().getId());
        verify(cache, times(1)).get(any());
        verify(apiClient, times(0)).getTargeting(any(), any(), anyLong());
        verify(cache, times(0)).put(any(), eq(targetingResult.result()));
    }

    @Test
    public void shouldNotFailWhenNoIdsMapped() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of());
        verifyNoInteractions(apiClient);

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        assertThat(targetingResult.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenApiClientIsFailed() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(apiClient.getTargeting(any(), any(), anyLong())).thenReturn(null);

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        assertThat(targetingResult.result()).isNull();
    }

    @Test
    public void shouldNotFailWhenApiClientReturnsFailFuture() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        when(idsMapper.toIds(any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(apiClient.getTargeting(any(), any(), anyLong())).thenReturn(Future.failedFuture("File"));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(bidRequest, optableAttributes, 100);

        // then
        assertThat(targetingResult.result()).isNull();
    }

    private OptableAttributes givenOptableAttributes() {
        return OptableAttributes.builder()
                .gpp("gpp")
                .gppSid(Set.of(2))
                .build();
    }
}
