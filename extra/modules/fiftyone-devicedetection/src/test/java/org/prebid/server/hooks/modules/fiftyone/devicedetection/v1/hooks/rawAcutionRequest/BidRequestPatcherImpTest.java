package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

import java.util.Collections;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BidRequestPatcherImpTest {
    private static BiFunction<BidRequest, CollectedEvidence, BidRequest> buildHook(
            BiFunction<
                    Device,
                    CollectedEvidence,
                    EnrichmentResult> deviceRefiner
    ) throws Exception {
        final RawAuctionRequestHook hook = new FiftyOneDeviceDetectionRawAuctionRequestHook(
                mock(ModuleConfig.class),
                new DeviceEnricher(mock(Pipeline.class)) {
                    @Override
                    public EnrichmentResult populateDeviceInfo(
                            Device device,
                            CollectedEvidence collectedEvidence) {
                        return deviceRefiner.apply(device, collectedEvidence);
                    }
                });
        return (bidRequest, evidence) -> {
            final AuctionRequestPayload auctionRequestPayload = mock(AuctionRequestPayload.class);
            when(auctionRequestPayload.bidRequest()).thenReturn(bidRequest);
            final AuctionInvocationContext invocationContext = mock(AuctionInvocationContext.class);
            final ModuleContext moduleContext = ModuleContext.builder()
                    .collectedEvidence(evidence)
                    .build();
            when(invocationContext.moduleContext()).thenReturn(moduleContext);
            return hook.call(auctionRequestPayload, invocationContext)
                    .result()
                    .payloadUpdate()
                    .apply(auctionRequestPayload)
                    .bidRequest();
        };
    }

    @Test
    public void shouldReturnNullWhenRequestIsNull() throws Exception {
        // given
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                null
        );

        // when and then
        assertThat(requestPatcher.apply(null, null)).isNull();
    }

    @Test
    public void shouldReturnOldRequestWhenMergedDeviceIsNull() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] refinerCalled = { false };
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (device, evidence) -> {
                    refinerCalled[0] = true;
                    return EnrichmentResult.builder().build();
                }
        );

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence)).isEqualTo(bidRequest);
        assertThat(refinerCalled).containsExactly(true);
    }

    @Test
    public void shouldPassMergedEvidenceToDeviceRefiner() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final String fakeUA = "crystal-ball-navigator";
        final CollectedEvidence savedEvidence = CollectedEvidence.builder()
                .rawHeaders(Collections.emptySet())
                .deviceUA(fakeUA)
                .build();

        // when
        final boolean[] refinerCalled = { false };
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (device, collectedEvidence) -> {
                    assertThat(collectedEvidence.rawHeaders()).isEqualTo(savedEvidence.rawHeaders());
                    assertThat(collectedEvidence.deviceUA()).isEqualTo(fakeUA);
                    refinerCalled[0] = true;
                    return null;
                }
        );

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence)).isEqualTo(bidRequest);
        assertThat(refinerCalled).containsExactly(true);
    }

    @Test
    public void shouldInjectReturnedDevice() throws Exception {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final Device mergedDevice = Device.builder().build();

        // when
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (device, collectedEvidence) -> EnrichmentResult
                        .builder()
                        .enrichedDevice(mergedDevice)
                        .build());

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence).getDevice()).isEqualTo(mergedDevice);
    }
}
