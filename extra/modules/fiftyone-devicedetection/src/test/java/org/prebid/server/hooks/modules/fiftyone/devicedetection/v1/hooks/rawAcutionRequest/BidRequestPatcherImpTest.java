package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters.DeviceInfoBuilderMethodSet;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection.DeviceRefiner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestPatcherImpTest {
    private static BiFunction<BidRequest, CollectedEvidence, BidRequest> buildHook(
            BiConsumer<CollectedEvidence.CollectedEvidenceBuilder, BidRequest> bidRequestEvidenceCollector,
            DeviceRefiner deviceRefiner
    ) {
        return new FiftyOneDeviceDetectionRawAuctionRequestHook(
                null,
                deviceRefiner
        ) {
            @Override
            public BidRequest enrichDevice(BidRequest bidRequest, CollectedEvidence collectedEvidence) {
                return super.enrichDevice(bidRequest, collectedEvidence);
            }

            @Override
            protected void collectEvidence(CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder, BidRequest bidRequest) {
                bidRequestEvidenceCollector.accept(evidenceBuilder, bidRequest);
            }
        }::enrichDevice;
    }

    @Test
    public void shouldReturnNullWhenRequestIsNull() {
        // given
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                null,
                null
        );

        // when and then
        assertThat(requestPatcher.apply(null, null)).isNull();
    }

    @Test
    public void shouldReturnNullWhenMergedDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] refinerCalled = { false };
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (builder, request) -> {},
                new DeviceRefiner() {
                    @Override
                    public <DeviceInfoBox, DeviceInfoBoxBuilder> EnrichmentResult<DeviceInfoBox> enrichDeviceInfo(
                            DeviceInfo rawDeviceInfo,
                            CollectedEvidence collectedEvidence,
                            DeviceInfoBuilderMethodSet<DeviceInfoBox, DeviceInfoBoxBuilder>.Adapter writableAdapter
                    ) {
                        refinerCalled[0] = true;
                        final EnrichmentResult.EnrichmentResultBuilder<DeviceInfoBox> resultBuilder
                                = EnrichmentResult.builder();
                        return resultBuilder.build();
                    }
                }
        );

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence)).isNull();
        assertThat(refinerCalled).containsExactly(true);
    }

    @Test
    public void shouldPassMergedEvidenceToDeviceRefiner() {
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
                new DeviceRefiner() {
                    @Override
                    public <DeviceInfoBox, DeviceInfoBoxBuilder> EnrichmentResult<DeviceInfoBox> enrichDeviceInfo(
                            DeviceInfo rawDeviceInfo,
                            CollectedEvidence collectedEvidence,
                            DeviceInfoBuilderMethodSet<DeviceInfoBox, DeviceInfoBoxBuilder>.Adapter writableAdapter
                    ) {
                        assertThat(collectedEvidence.rawHeaders()).isEqualTo(savedEvidence.rawHeaders());
                        assertThat(collectedEvidence.deviceUA()).isEqualTo(fakeUA);
                        refinerCalled[0] = true;
                        return null;
                    }
                }
        );

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence)).isNull();
        assertThat(refinerCalled).containsExactly(true);
    }

    @Test
    public void shouldInjectReturnedDevice() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final Device mergedDevice = Device.builder().build();

        // when
        final BiFunction<BidRequest, CollectedEvidence, BidRequest> requestPatcher = buildHook(
                (builder, request) -> {},
                new DeviceRefiner() {
                    @Override
                    public <DeviceInfoBox, DeviceInfoBoxBuilder> EnrichmentResult<DeviceInfoBox> enrichDeviceInfo(
                            DeviceInfo rawDeviceInfo,
                            CollectedEvidence collectedEvidence,
                            DeviceInfoBuilderMethodSet<DeviceInfoBox, DeviceInfoBoxBuilder>.Adapter writableAdapter
                    ) {
                        final EnrichmentResult.EnrichmentResultBuilder<DeviceInfoBox> resultBuilder
                                = EnrichmentResult.builder();
                        return resultBuilder.enrichedDevice((DeviceInfoBox) mergedDevice).build();
                    }
                });

        // then
        assertThat(requestPatcher.apply(bidRequest, savedEvidence).getDevice()).isEqualTo(mergedDevice);
    }
}
