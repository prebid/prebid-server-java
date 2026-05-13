package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OptableBidderRequestHookTest extends BaseOptableTest {

    @Mock
    private BidderInvocationContext invocationContext;

    @Mock
    private BidderRequestPayload bidderRequestPayload;

    private OptableBidderRequestHook target;

    @BeforeEach
    public void setUp() {
        target = new OptableBidderRequestHook();
        when(bidderRequestPayload.bidRequest()).thenReturn(givenBidRequest());
    }

    @Test
    public void shouldHaveRightCode() {
        // given and when and then
        assertThat(target.code()).isEqualTo("optable-targeting-bidder-request-hook");
    }

    @Test
    public void shouldReturnNoActionWhenPerBidderEnrichmentIsDisabled() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenOptableTargetingProperties(false));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.moduleContext()).isSameAs(moduleContext);
    }

    @Test
    public void shouldReturnNoActionWhenBiddersToEnrichIsEmpty() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Collections.emptySet());
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void shouldReturnNoActionWhenBiddersToEnrichIsNull() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void shouldReturnUpdateActionWhenTargetingResultIsAvailable() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setOptableTargetingCall(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors()).isNull();

        final BidRequest enrichedRequest = result
                .payloadUpdate()
                .apply(BidderRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(enrichedRequest.getUser().getEids().getFirst().getUids().getFirst().getId())
                .isEqualTo("id");
        assertThat(enrichedRequest.getUser().getData().getFirst().getSegment().getFirst().getId())
                .isEqualTo("id");
    }

    @Test
    public void shouldUpdateModuleContextWithTargetingOnSuccess() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setOptableTargetingCall(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        target.call(bidderRequestPayload, invocationContext);

        // then — the moduleContext is mutated in-place with audience targeting
        assertThat(moduleContext.getTargeting()).isNotNull().isNotEmpty();
        assertThat(moduleContext.getEnrichRequestStatus()).isNotNull();
        assertThat(moduleContext.getEnrichRequestStatus().getStatus().getValue()).isEqualTo("success");
    }

    @Test
    public void shouldReturnNoActionWhenTargetingCallFails() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setOptableTargetingCall(
                Future.failedFuture(new RuntimeException("targeting service error")));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void shouldIncludeAnalyticsTagsInNoActionResponse() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenOptableTargetingProperties(false));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.analyticsTags()).isNotNull();
        assertThat(result.analyticsTags().activities()).isNotEmpty();
        assertThat(result.analyticsTags().activities().getFirst().name())
                .isEqualTo("optable-enrich-request");
    }

    @Test
    public void shouldIncludeAnalyticsTagsInUpdateResponse() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setOptableTargetingCall(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result.analyticsTags()).isNotNull();
        assertThat(result.analyticsTags().activities()).isNotEmpty();
        assertThat(result.analyticsTags().activities().getFirst().name())
                .isEqualTo("optable-enrich-request");
    }

    private static ModuleContext givenModuleContextWithProperties(OptableTargetingProperties properties) {
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setOptableTargetingProperties(properties);
        return moduleContext;
    }

    private OptableTargetingProperties givenPropertiesWithPerBidderEnrichmentEnabled() {
        final OptableTargetingProperties properties = givenOptableTargetingProperties(false);
        properties.setEnrichmentPercentage(50);
        return properties;
    }
}
