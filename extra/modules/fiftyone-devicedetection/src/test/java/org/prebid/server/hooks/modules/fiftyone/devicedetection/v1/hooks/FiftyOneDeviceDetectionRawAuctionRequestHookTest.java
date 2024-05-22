package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FiftyOneDeviceDetectionRawAuctionRequestHookTest {
    private static FiftyOneDeviceDetectionRawAuctionRequestHook buildHook(
            Predicate<AuctionInvocationContext> accountControl,
            BiConsumer<CollectedEvidence.CollectedEvidenceBuilder, BidRequest> bidRequestEvidenceCollector,
            BiFunction<ModuleContext, Consumer<CollectedEvidence.CollectedEvidenceBuilder>, ModuleContext> moduleContextPatcher,
            BiFunction<BidRequest, CollectedEvidence, BidRequest> bidRequestPatcher
    ) {
        return new FiftyOneDeviceDetectionRawAuctionRequestHook(
                null,
                null
        ) {
            @Override
            protected boolean isAccountAllowed(AuctionInvocationContext invocationContext) {
                return accountControl.test(invocationContext);
            }

            @Override
            protected BidRequest enrichDevice(BidRequest bidRequest, CollectedEvidence collectedEvidence) {
                return bidRequestPatcher.apply(bidRequest, collectedEvidence);
            }

            @Override
            protected ModuleContext addEvidenceToContext(ModuleContext moduleContext, Consumer<CollectedEvidence.CollectedEvidenceBuilder> evidenceInjector) {
                return moduleContextPatcher.apply(moduleContext, evidenceInjector);
            }

            @Override
            protected void collectEvidence(CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder, BidRequest bidRequest) {
                bidRequestEvidenceCollector.accept(evidenceBuilder, bidRequest);
            }
        };
    }

    @Test
    public void codeShouldStartWithModuleCode() {
        // given
        final RawAuctionRequestHook hook = buildHook(
                null,
                null,
                null,
                null);

        // when and then
        assertThat(hook.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }

    @Test
    public void shouldPassInvocationContextToAccountControl() {
        // given
        final AuctionInvocationContext mockedContext = mock(AuctionInvocationContext.class);

        // when
        final boolean[] accountControlCalled = { false };
        final RawAuctionRequestHook hook = buildHook(
                invocationContext -> {
                    accountControlCalled[0] = true;
                    return false;
                },
                null,
                null,
                null);
        final Future<InvocationResult<AuctionRequestPayload>> result = hook.call(
                null,
                mockedContext
        );

        // then
        assertThat(result.succeeded()).isTrue();
        final InvocationResult<AuctionRequestPayload> fullResult = result.result();
        assertThat(fullResult.action()).isEqualTo(InvocationAction.no_action);
        assertThat(fullResult.moduleContext()).isNull();
        assertThat(accountControlCalled).containsExactly(true);
    }

    @Test
    public void shouldPassPayloadAndBuilderThroughModulePatcher() {
        // given
        final AuctionRequestPayload payload = mock(AuctionRequestPayload.class);
        final BidRequest rawBidRequest = BidRequest.builder().build();
        when(payload.bidRequest()).thenReturn(rawBidRequest);
        final AuctionInvocationContext rawInvocationContext = mock(AuctionInvocationContext.class);
        final ModuleContext existingContext = ModuleContext.builder().build();
        when(rawInvocationContext.moduleContext()).thenReturn(existingContext);

        final CollectedEvidence.CollectedEvidenceBuilder builder = CollectedEvidence.builder();

        // when
        final boolean[] payloadReceived = { false };
        final boolean[] patcherCalled = { false };
        final RawAuctionRequestHook hook = buildHook(
                invocationContext -> true,
                (evidenceBuilder, bidRequest) -> {
                    assertThat(evidenceBuilder).isEqualTo(builder);
                    assertThat(bidRequest).isEqualTo(rawBidRequest);
                    payloadReceived[0] = true;
                },
                (moduleContext, collectedEvidenceBuilderConsumer) -> {
                    assertThat(moduleContext).isEqualTo(existingContext);
                    patcherCalled[0] = true;
                    collectedEvidenceBuilderConsumer.accept(builder);
                    return null;
                },
                null);
        final Future<InvocationResult<AuctionRequestPayload>> result = hook.call(payload, rawInvocationContext);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(patcherCalled).containsExactly(true);
        assertThat(payloadReceived).containsExactly(true);

        final InvocationResult<AuctionRequestPayload> fullResult = result.result();
        assertThat(fullResult.action()).isEqualTo(InvocationAction.update);
        assertThat(fullResult.moduleContext()).isNull();
    }

    @Test
    public void shouldReturnOldPayloadIfPatcherReturnedNull() {
        // given
        final AuctionRequestPayload payload = mock(AuctionRequestPayload.class);
        final BidRequest rawBidRequest = BidRequest.builder().build();
        when(payload.bidRequest()).thenReturn(rawBidRequest);
        final AuctionInvocationContext rawInvocationContext = mock(AuctionInvocationContext.class);
        final ModuleContext newContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .deviceUA("agent-smith")
                        .build())
                .build();

        // when
        final boolean[] patcherCalled = { false };
        final RawAuctionRequestHook hook = buildHook(
                invocationContext -> true,
                (evidenceBuilder, bidRequest) -> fail("Evidence builder should not be called"),
                (moduleContext, collectedEvidenceBuilderConsumer) -> newContext,
                (bidRequest, collectedEvidence) -> {
                    assertThat(bidRequest).isEqualTo(payload.bidRequest());
                    assertThat(collectedEvidence).isEqualTo(newContext.collectedEvidence());
                    patcherCalled[0] = true;
                    return null;
                });
        final Future<InvocationResult<AuctionRequestPayload>> result = hook.call(payload, rawInvocationContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> fullResult = result.result();
        assertThat(fullResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(fullResult.action()).isEqualTo(InvocationAction.update);
        assertThat(fullResult.payloadUpdate().apply(payload)).isEqualTo(payload);

        assertThat(patcherCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnOldPayloadIfPatcherReturnedOldRequest() {
        // given
        final AuctionRequestPayload payload = mock(AuctionRequestPayload.class);
        when(payload.bidRequest()).thenReturn(BidRequest.builder().build());
        final ModuleContext newContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .deviceUA("agent-smith")
                        .build())
                .build();

        // when
        final boolean[] patcherCalled = { false };
        final RawAuctionRequestHook hook = buildHook(
                invocationContext -> true,
                (evidenceBuilder, bidRequest) -> fail("Evidence builder should not be called"),
                (moduleContext, collectedEvidenceBuilderConsumer) -> newContext,
                (bidRequest, collectedEvidence) -> {
                    assertThat(bidRequest).isEqualTo(payload.bidRequest());
                    assertThat(collectedEvidence).isEqualTo(newContext.collectedEvidence());
                    patcherCalled[0] = true;
                    return bidRequest;
                });
        final Future<InvocationResult<AuctionRequestPayload>> result = hook.call(
                payload,
                mock(AuctionInvocationContext.class)
        );

        // then
        assertThat(result.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> fullResult = result.result();
        assertThat(fullResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(fullResult.action()).isEqualTo(InvocationAction.update);
        assertThat(fullResult.payloadUpdate().apply(payload)).isEqualTo(payload);

        assertThat(patcherCalled).containsExactly(true);
    }

    @Test
    public void shouldReturnNewPayloadIfPatcherReturnedNewRequest() {
        // given
        final AuctionRequestPayload payload = mock(AuctionRequestPayload.class);
        when(payload.bidRequest()).thenReturn(BidRequest.builder()
                .id("4519")
                .build());
        final BidRequest patchedRequest = BidRequest.builder()
                .id("2704")
                .build();
        final ModuleContext newContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .deviceUA("agent-smith")
                        .build())
                .build();

        // when
        final boolean[] patcherCalled = { false };
        final RawAuctionRequestHook hook = buildHook(
                invocationContext -> true,
                (evidenceBuilder, bidRequest) -> fail("Evidence builder should not be called"),
                (moduleContext, collectedEvidenceBuilderConsumer) -> newContext,
                (bidRequest, collectedEvidence) -> {
                    assertThat(bidRequest).isEqualTo(payload.bidRequest());
                    assertThat(collectedEvidence).isEqualTo(newContext.collectedEvidence());
                    patcherCalled[0] = true;
                    return patchedRequest;
                });
        final Future<InvocationResult<AuctionRequestPayload>> result = hook.call(
                payload,
                mock(AuctionInvocationContext.class)
        );

        // then
        assertThat(result.succeeded()).isTrue();

        final InvocationResult<AuctionRequestPayload> fullResult = result.result();
        assertThat(fullResult.status()).isEqualTo(InvocationStatus.success);
        assertThat(fullResult.action()).isEqualTo(InvocationAction.update);

        final AuctionRequestPayload patchedPayload = fullResult.payloadUpdate().apply(payload);
        assertThat(patchedPayload).isNotEqualTo(payload);
        assertThat(patchedPayload.bidRequest()).isEqualTo(patchedRequest);

        assertThat(patcherCalled).containsExactly(true);
    }
}
