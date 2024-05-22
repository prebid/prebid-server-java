package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeviceTypeConverterImpTest {
    private static Integer convertDeviceType(String deviceType) {
        return new DeviceDataWrapper(null) {
            @Override
            public Integer convertDeviceType(String deviceType) {
                return super.convertDeviceType(deviceType);
            }
        }.convertDeviceType(deviceType);
    }

    @Test
    public void shouldReturnFourForPhone() {
        // given
        final String typeString = "Phone";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isEqualTo(4);
    }

    @Test
    public void shouldReturnSevenForMediaHub() {
        // given
        final String typeString = "MediaHub";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isEqualTo(7);
    }

    @Test
    public void shouldReturnNullForUnexpectedValue() {
        // given
        final String typeString = "BattleStar Atlantis";

        // when
        final Integer foundValue = convertDeviceType(typeString);

        // then
        assertThat(foundValue).isNull();
    }
}
