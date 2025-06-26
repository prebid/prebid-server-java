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
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClientFactory;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClientImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.CachedAPIClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableTargetingTest extends BaseOptableTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Cache cache;

    @Mock
    private IdsMapper idsMapper;

    @Mock
    private APIClientImpl apiClient;

    @Mock
    private CachedAPIClient cachingAPIClient;

    private APIClientFactory apiClientFactory;

    private QueryBuilder queryBuilder = new QueryBuilder();

    private OptableTargeting target;

    private OptableAttributes optableAttributes;

    private OptableTargetingProperties properties;

    @BeforeEach
    public void setUp() {
        apiClientFactory = new APIClientFactory(apiClient, cachingAPIClient, true);
        optableAttributes = givenOptableAttributes();
        properties = givenOptableTargetingProperties(true);
        target = new OptableTargeting(idsMapper, queryBuilder, apiClientFactory);
    }

    @Test
    public void shouldUseNonCachedAPIClient() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        properties = givenOptableTargetingProperties(false);
        when(idsMapper.toIds(any(), any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(apiClient.getTargeting(any(), any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(
                properties, bidRequest, optableAttributes, 100);

        // then
        assertThat(targetingResult.result()).isNotNull();
        verify(apiClient).getTargeting(any(), any(), any(), anyLong());
    }

    @Test
    public void shouldUseCachedAPIClient() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        properties = givenOptableTargetingProperties(true);
        when(idsMapper.toIds(any(), any())).thenReturn(List.of(Id.of(Id.ID5, "id")));
        when(cachingAPIClient.getTargeting(any(), any(), any(), anyLong()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        // when
        final Future<TargetingResult> targetingResult = target.getTargeting(
                properties, bidRequest, optableAttributes, 100);

        // then
        assertThat(targetingResult.result()).isNotNull();
        verify(cachingAPIClient).getTargeting(any(), any(), any(), anyLong());
    }

    private OptableAttributes givenOptableAttributes() {
        return OptableAttributes.builder()
                .gpp("gpp")
                .gppSid(Set.of(2))
                .build();
    }
}
