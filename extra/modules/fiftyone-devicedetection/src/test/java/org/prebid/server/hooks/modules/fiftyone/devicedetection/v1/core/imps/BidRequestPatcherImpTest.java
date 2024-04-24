package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestPatcher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class BidRequestPatcherImpTest {
    @Test
    public void shouldReturnNullWhenRequestIsNull() {
        // given
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                null,
                null,
                null,
                null
        );

        // when and then
        assertThat(requestPatcher.combine(null, null)).isNull();
    }

    @Test
    public void shouldReturnNullWhenPlanIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                // when
                device -> null,
                null,
                null,
                null
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, null)).isNull();
    }

    @Test
    public void shouldReturnNullWhenPlanIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                // when
                device -> new DevicePatchPlan(Collections.emptySet()),
                null,
                null,
                null
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, null)).isNull();
    }

    @Test
    public void shouldPassBidRequestDeviceToPatchPlanner() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().build())
                .build();

        // when
        final boolean[] devicePassed = { false };
        new BidRequestPatcherImp(
                device -> {
                    assertThat(device).isEqualTo(bidRequest.getDevice());
                    devicePassed[0] = true;
                    return null;
                },
                null,
                null,
                null
        ).combine(bidRequest, null);

        // then
        assertThat(devicePassed).containsExactly(true);
    }

    @Test
    public void shouldPassBidRequestDeviceToEvidenceCollector() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] requestPassed = { false };
        new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> {
                    assertThat(request).isEqualTo(bidRequest);
                    requestPassed[0] = true;
                },
                (evidence, plan) -> null,
                null
        ).combine(bidRequest, savedEvidence);

        // then
        assertThat(requestPassed).containsExactly(true);
    }

    @Test
    public void shouldReturnNullIfDetectedDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] detectorCalled = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> {},
                (evidence, plan) -> {
                    detectorCalled[0] = true;
                    return null;
                },
                null
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(detectorCalled).containsExactly(true);
    }

    @Test
    public void shouldPassPatchPlanToDeviceDetector() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final DevicePatchPlan patchPlan = makeDummyPatchPlan();

        // when
        final boolean[] planPassed = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> patchPlan,
                (builder, request) -> {},
                (evidence, plan) -> {
                    planPassed[0] = true;
                    return null;
                },
                null
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(planPassed).containsExactly(true);
    }

    @Test
    public void shouldPassMergedEvidenceToDeviceDetector() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder()
                .rawHeaders(Collections.emptySet())
                .build();
        final String fakeUA = "crystal-ball-navigator";

        // when
        final boolean[] evidenceMerged = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> builder.deviceUA(fakeUA),
                (evidence, plan) -> {
                    assertThat(evidence.rawHeaders()).isEqualTo(savedEvidence.rawHeaders());
                    assertThat(evidence.deviceUA()).isEqualTo(fakeUA);
                    evidenceMerged[0] = true;
                    return null;
                },
                null
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(evidenceMerged).containsExactly(true);
    }

    @Test
    public void shouldReturnNullWhenMergedDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] patcherCalled = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> {},
                (evidence, plan) -> mock(DeviceInfo.class),
                (device, plan, newData) -> {
                    patcherCalled[0] = true;
                    return null;
                }
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(patcherCalled).containsExactly(true);
    }

    @Test
    public void shouldPassPatchPlanToDevicePatcher() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final DevicePatchPlan patchPlan = makeDummyPatchPlan();

        // when
        final boolean[] planPassed = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> patchPlan,
                (builder, request) -> {},
                (evidence, plan) -> mock(DeviceInfo.class),
                (device, plan, newData) -> {
                    assertThat(plan).isEqualTo(patchPlan);
                    planPassed[0] = true;
                    return null;
                }
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(planPassed).containsExactly(true);
    }

    @Test
    public void shouldPassDeviceToDevicePatcher() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().build())
                .build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();

        // when
        final boolean[] devicePassed = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> {},
                (evidence, plan) -> mock(DeviceInfo.class),
                (device, plan, newData) -> {
                    assertThat(device).isEqualTo(bidRequest.getDevice());
                    devicePassed[0] = true;
                    return null;
                }
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(devicePassed).containsExactly(true);
    }

    @Test
    public void shouldPassNewDataToDevicePatcher() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final DeviceInfo detectedData = mock(DeviceInfo.class);

        // when
        final boolean[] newDataPassed = { false };
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> {},
                (evidence, plan) -> detectedData,
                (device, plan, newData) -> {
                    assertThat(newData).isEqualTo(detectedData);
                    newDataPassed[0] = true;
                    return null;
                }
        );

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence)).isNull();
        assertThat(newDataPassed).containsExactly(true);
    }

    @Test
    public void shouldInjectReturnedDevice() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final CollectedEvidence savedEvidence = CollectedEvidence.builder().build();
        final Device mergedDevice = Device.builder().build();

        // when
        final BidRequestPatcher requestPatcher = new BidRequestPatcherImp(
                device -> makeDummyPatchPlan(),
                (builder, request) -> {},
                (evidence, plan) -> mock(DeviceInfo.class),
                (device, plan, newData) -> mergedDevice);

        // then
        assertThat(requestPatcher.combine(bidRequest, savedEvidence).getDevice()).isEqualTo(mergedDevice);
    }

    private static DevicePatchPlan makeDummyPatchPlan() {
        return new DevicePatchPlan(List.of(
                new AbstractMap.SimpleEntry<>("fakeKey", (deviceBuilder, oldDevice, newData) -> true)));
    }
}
