package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeoutException;

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
        when(invocationContext.bidder()).thenReturn("bidder1");
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
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
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
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
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
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);
    }

    @Test
    public void shouldReturnUpdateActionWhenTargetingResultIsAvailable() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis() - 100);
        moduleContext.setOptableTargetingCall(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.bidder()).thenReturn("bidder1");

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.update, InvocationResult::action)
                .extracting(InvocationResult::errors).isNull();

        final BidRequest enrichedRequest = result
                .payloadUpdate()
                .apply(BidderRequestPayloadImpl.of(givenBidRequest()))
                .bidRequest();
        assertThat(enrichedRequest.getUser().getEids().getFirst().getUids().getFirst().getId())
                .isEqualTo("id");
        assertThat(enrichedRequest.getUser().getData().getFirst().getSegment().getFirst().getId())
                .isEqualTo("id");

        assertAnalyticsTags(result.analyticsTags(), "bidder1", "enriched", "success");
    }

    @Test
    public void shouldUpdateModuleContextWithTargetingOnSuccess() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis() - 100);
        moduleContext.setOptableTargetingCall(Future.succeededFuture(givenTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.bidder()).thenReturn("bidder1");

        // when
        target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(moduleContext.getTargeting()).isNotNull().isNotEmpty();
        assertThat(moduleContext.getEnrichRequestStatus()).isNotNull()
                .extracting(EnrichmentStatus::getStatus)
                .extracting(Status::getValue)
                .isEqualTo("success");
    }

    @Test
    public void shouldReturnNoActionWithNoDataOutcomeWhenTargetingResultHasNoUser() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis() - 50);
        moduleContext.setOptableTargetingCall(Future.succeededFuture(givenEmptyTargetingResult()));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.bidder()).thenReturn("bidder1");

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);

        assertThat(moduleContext.getTargeting()).isNull();
        assertAnalyticsTags(result.analyticsTags(), "bidder1", "no-data", "fail");
    }

    @Test
    public void shouldReturnNoActionWithErrorOutcomeWhenTargetingCallFails() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis() - 50);
        moduleContext.setOptableTargetingCall(
                Future.failedFuture(new RuntimeException("targeting service error")));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.bidder()).thenReturn("bidder1");

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);

        assertAnalyticsTags(result.analyticsTags(), "bidder1", "error", "fail");
    }

    @Test
    public void shouldReturnNoActionWithTimeoutOutcomeWhenTargetingCallTimesOut() {
        // given
        final ModuleContext moduleContext = givenModuleContextWithProperties(
                givenPropertiesWithPerBidderEnrichmentEnabled());
        moduleContext.setBiddersToEnrich(Set.of("bidder1"));
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis() - 500);
        moduleContext.setOptableTargetingCall(
                Future.failedFuture(new TimeoutException("Timeout has been exceeded")));
        when(invocationContext.moduleContext()).thenReturn(moduleContext);
        when(invocationContext.bidder()).thenReturn("bidder1");

        // when
        final Future<InvocationResult<BidderRequestPayload>> future =
                target.call(bidderRequestPayload, invocationContext);

        // then
        assertThat(future.succeeded()).isTrue();
        final InvocationResult<BidderRequestPayload> result = future.result();
        assertThat(result).isNotNull()
                .returns(InvocationStatus.success, InvocationResult::status)
                .returns(InvocationAction.no_action, InvocationResult::action);

        assertAnalyticsTags(result.analyticsTags(), "bidder1", "timeout", "fail");
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

    private void assertAnalyticsTags(Tags tags, String expectedBidder, String expectedOutcome, String expectedStatus) {
        assertThat(tags).isNotNull();
        assertThat(tags.activities()).hasSize(1);
        assertThat(tags.activities().getFirst().name()).isEqualTo("optable-enrich-bidder-request");
        assertThat(tags.activities().getFirst().status()).isEqualTo(expectedStatus);

        assertThat(tags.activities().getFirst().results()).hasSize(1);
        final JsonNode values = tags.activities().getFirst().results().getFirst().values();
        assertThat(values.get("outcome").asText()).isEqualTo(expectedOutcome);
        assertThat(values.has("execution-time")).isTrue();

        assertThat(tags.activities().getFirst().results().getFirst().appliedTo()).isNotNull();
        assertThat(tags.activities().getFirst().results().getFirst().appliedTo().bidders())
                .containsExactly(expectedBidder);
    }
}
