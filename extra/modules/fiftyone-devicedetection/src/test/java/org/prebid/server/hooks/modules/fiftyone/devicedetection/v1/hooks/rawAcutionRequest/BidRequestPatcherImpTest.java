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

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class BidRequestPatcherImpTest {
    private static BiFunction<BidRequest, CollectedEvidence, BidRequest> buildHook(
            BiConsumer<CollectedEvidence.CollectedEvidenceBuilder, BidRequest> bidRequestEvidenceCollector,
            BiFunction<
                    Device,
                    CollectedEvidence,
                    EnrichmentResult> deviceRefiner
    ) throws Exception {

        return new FiftyOneDeviceDetectionRawAuctionRequestHook(
                mock(ModuleConfig.class),
                new DeviceEnricher(mock(Pipeline.class)) {
                    @Override
                    public EnrichmentResult populateDeviceInfo(
                            Device device,
                            CollectedEvidence collectedEvidence) {

                        return deviceRefiner.apply(device, collectedEvidence);
                    }
                }) {
            @Override
            public BidRequest enrichDevice(BidRequest bidRequest, CollectedEvidence collectedEvidence) {

                return super.enrichDevice(bidRequest, collectedEvidence);
            }

            @Override
            protected void collectEvidence(
                    CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder,
                    BidRequest bidRequest) {

                bidRequestEvidenceCollector.accept(evidenceBuilder, bidRequest);
            }
        }
            ::enrichDevice;
    }

    @Test
    public void shouldReturnNullWhenRequestIsNull() throws Exception {

        // given
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                null,
                null
        );

        // when and then
        assertThat(requestPatcher.apply(null, null)).isNull();
    }

    @Test
    public void shouldReturnNullWhenMergedDeviceIsNull() throws Exception {

        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] refinerCalled = { false };
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (builder, request) -> { },
                (device, evidence) -> {
                    refinerCalled[0] = true;
                    return EnrichmentResult.builder().build();
                }
        );

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence)).isNull();
        assertThat(refinerCalled).containsExactly(true);
    }

    @Test
    public void shouldPassMergedEvidenceToDeviceRefiner() throws Exception {

        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder()
                .rawHeaders(Collections.emptySet())
                .build();
        final String fakeUA = "crystal-ball-navigator";

        // when
        final boolean[] refinerCalled = { false };
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (builder, request) -> builder.deviceUA(fakeUA),
                (device, collectedEvidence) -> {
                    assertThat(collectedEvidence.rawHeaders()).isEqualTo(savedEvidence.rawHeaders());
                    assertThat(collectedEvidence.deviceUA()).isEqualTo(fakeUA);
                    refinerCalled[0] = true;
                    return null;
                }
        );

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence)).isNull();
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
                (builder, request) -> { },
                (device, collectedEvidence) -> EnrichmentResult
                        .builder()
                        .enrichedDevice(mergedDevice)
                        .build());

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence).getDevice()).isEqualTo(mergedDevice);
    }
}
