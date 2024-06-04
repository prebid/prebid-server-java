package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.adapters;

import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DeviceTypeConverterImpTest {
    private static Integer convertDeviceType(String deviceType) throws Exception {

        return new DeviceEnricher(mock(Pipeline.class)) {

            @Override
            public Integer convertDeviceType(String deviceType) {

                return super.convertDeviceType(deviceType);
            }
        }.convertDeviceType(deviceType);
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
    public void shouldReturnZeroForUnexpectedValue() throws Exception {

        // given
        final String typeString = "BattleStar Atlantis";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isEqualTo(0);
    }
}
