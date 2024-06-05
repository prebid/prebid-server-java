package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.adapters;

import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import fiftyone.pipeline.engines.exceptions.NoValueException;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;
import org.prebid.server.util.ObjectUtil;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceTypeConverterImpTest {
    private static Integer convertDeviceType(String deviceType) throws Exception {
        final Pipeline pipeline = mock(Pipeline.class);
        final FlowData flowData = mock(FlowData.class);
        when(pipeline.createFlowData()).thenReturn(flowData);
        final DeviceData deviceData = mock(DeviceData.class);
        when(flowData.get(DeviceData.class)).thenReturn(deviceData);
        when(deviceData.getDeviceType()).thenReturn(mockValue(deviceType));
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        final DeviceEnricher deviceEnricher = new DeviceEnricher(pipeline);
        final EnrichmentResult result = deviceEnricher.populateDeviceInfo(null, collectedEvidence);
        return Optional.ofNullable(result)
                .map(EnrichmentResult::enrichedDevice)
                .map(Device::getDevicetype)
                .orElse(null);
    }

    private static <T> AspectPropertyValue<T> mockValue(T value) {
        return new AspectPropertyValue<>() {
            @Override
            public boolean hasValue() {
                return true;
            }

            @Override
            public T getValue() throws NoValueException {
                return value;
            }

            @Override
            public void setValue(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getNoValueMessage() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setNoValueMessage(String s) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    public void shouldReturnFourForPhone() throws Exception {
        // given
        final String typeString = "Phone";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isEqualTo(4);
    }

    @Test
    public void shouldReturnSevenForMediaHub() throws Exception {
        // given
        final String typeString = "MediaHub";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isEqualTo(7);
    }

    @Test
    public void shouldReturnNullForUnexpectedValue() throws Exception {
        // given
        final String typeString = "BattleStar Atlantis";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isNull();
    }
}
