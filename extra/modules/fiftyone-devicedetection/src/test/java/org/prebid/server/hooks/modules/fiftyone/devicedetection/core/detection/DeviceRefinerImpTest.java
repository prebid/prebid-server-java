package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.adapters.DeviceMirror;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

public class DeviceRefinerImpTest {
    private static DeviceRefiner buildRefiner(
            Function<DeviceInfo,
                    Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>>> devicePatchPlanner,
            DeviceDetector deviceDetector)
    {
        return new DeviceRefinerImp(null) {
            @Override
            protected Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> buildPatchPlanFor(
                    DeviceInfo deviceInfo
            ) {
                return devicePatchPlanner.apply(deviceInfo);
            }

            @Override
            protected boolean populateDeviceInfo(
                    WritableDeviceInfo writableDeviceInfo,
                    CollectedEvidence collectedEvidence,
                    Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> devicePatchPlan,
                    EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder)
            {
                return deviceDetector.populateDeviceInfo(
                        writableDeviceInfo,
                        collectedEvidence,
                        devicePatchPlan,
                        enrichmentResultBuilder
                );
            }
        };
    }

    @Test
    public void shouldReturnNoDeviceIfPlanIsNull() {
        // given

        // when
        final DeviceRefiner deviceRefiner = buildRefiner(
                deviceInfo -> null,
                null
        );
        final EnrichmentResult<Device> result = deviceRefiner.enrichDeviceInfo(
                null,
                null,
                DeviceMirror.BUILDER_METHOD_SET.makeAdapter(Device.builder().build())
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.enrichedDevice()).isNull();
    }

    @Test
    public void shouldReturnNoDeviceIfPlanIsEmpty() {
        // given

        // when
        final DeviceRefiner deviceRefiner = buildRefiner(
                deviceInfo -> Collections.emptySet(),
                null
        );
        final EnrichmentResult<Device> result = deviceRefiner.enrichDeviceInfo(
                null,
                null,
                DeviceMirror.BUILDER_METHOD_SET.makeAdapter(Device.builder().build())
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.enrichedDevice()).isNull();
    }

    @Test
    public void shouldReturnNoDeviceIfPropertiesNotFound() {
        // given

        // when
        final DeviceRefiner deviceRefiner = buildRefiner(
                deviceInfo -> Collections.singletonList(
                        entry("dummy", (writableDeviceInfo, newData) -> false)),
                (writableDeviceInfo, collectedEvidence, devicePatchPlan, enrichmentResultBuilder) -> false
        );
        final EnrichmentResult<Device> result = deviceRefiner.enrichDeviceInfo(
                null,
                null,
                DeviceMirror.BUILDER_METHOD_SET.makeAdapter(Device.builder().build())
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.enrichedDevice()).isNull();
    }

    @Test
    public void shouldReturnNewDeviceIfPropertiesAreFound() {
        // given

        // when
        final DeviceRefiner deviceRefiner = buildRefiner(
                deviceInfo -> Collections.singletonList(
                        entry("dummy", (writableDeviceInfo, newData) -> false)),
                (writableDeviceInfo, collectedEvidence, devicePatchPlan, enrichmentResultBuilder) -> true
        );
        final EnrichmentResult<Device> result = deviceRefiner.enrichDeviceInfo(
                null,
                null,
                DeviceMirror.BUILDER_METHOD_SET.makeAdapter(Device.builder().build())
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.enrichedDevice()).isNotNull();
    }
}
