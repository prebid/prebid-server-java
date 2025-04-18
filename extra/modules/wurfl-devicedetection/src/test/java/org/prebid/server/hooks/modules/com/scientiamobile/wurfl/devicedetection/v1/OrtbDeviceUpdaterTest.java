package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockConnectedTv;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockDesktop;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockMobileUndefinedDevice;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockOttDevice;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockTablet;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockUnknownDevice;

@Slf4j
@ExtendWith(MockitoExtension.class)
class OrtbDeviceUpdaterTest {

    private OrtbDeviceUpdater target;
    private Set<String> staticCaps;
    private Set<String> virtualCaps;

    @BeforeEach
    void setUp() {
        target = new OrtbDeviceUpdater();
        staticCaps = Set.of("ajax_support_javascript", "brand_name", "density_class",
                "is_connected_tv", "is_ott", "is_tablet", "model_name", "resolution_height", "resolution_width");
        virtualCaps = Set.of("advertised_device_os", "advertised_device_os_version",
                "is_full_desktop", "pixel_density");
    }

    @Test
    void updateShouldUpdateDeviceMakeWhenOriginalIsEmpty() {
        // given
        final var wurflDevice = mockIPhone();
        final Device device = Device.builder().build();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getMake()).isEqualTo("Apple");
        assertThat(result.getDevicetype()).isEqualTo(1);
    }

    @Test
    void updateShouldNotUpdateDeviceMakeWhenOriginalExists() {
        // given
        final Device device = Device.builder().make("Samsung").build();
        final var wurflDevice = mockIPhone();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getMake()).isEqualTo("Samsung");
    }

    @Test
    void updateShouldNotUpdateDeviceMakeWhenOriginalBigIntegerExists() {
        // given
        final Device device = Device.builder().make("Apple").pxratio(new BigDecimal("1.0")).build();
        final var wurflDevice = mockIPhone();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getMake()).isEqualTo("Apple");
        assertThat(result.getPxratio()).isEqualTo("1.0");
    }

    @Test
    void updateShouldUpdateDeviceModelWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockIPhone();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getModel()).isEqualTo("iPhone");
    }

    @Test
    void updateShouldUpdateDeviceOsWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockIPhone();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getOs()).isEqualTo("iOS");
    }

    @Test
    void updateShouldUpdateResolutionWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockIPhone();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getW()).isEqualTo(3200);
        assertThat(result.getH()).isEqualTo(1440);
    }

    @Test
    void updateShouldHandleJavascriptSupport() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockIPhone();

        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getJs()).isEqualTo(1);
    }

    @Test
    void updateShouldHandleOttDeviceType() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockOttDevice();
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        assertThat(result.getDevicetype()).isEqualTo(7);
    }

    @Test
    void updateShouldReturnDeviceOtherMobileWhenMobileIsNotPhoneOrTablet() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockMobileUndefinedDevice();
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);
        // then
        assertThat(result.getDevicetype()).isEqualTo(6);
    }

    @Test
    void updateShouldReturnNullWhenMobileTypeIsUnknown() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockUnknownDevice();
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);
        // then
        assertThat(result.getDevicetype()).isNull();
    }

    @Test
    void updateShouldHandlePersonalComputerDeviceType() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockDesktop();
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);
        // then
        assertThat(result.getDevicetype()).isEqualTo(2);
    }

    @Test
    void updateShouldHandleConnectedTvDeviceType() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockConnectedTv();
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);
        // then
        assertThat(result.getDevicetype()).isEqualTo(3);
    }

    @Test
    void updateShouldNotUpdateDeviceTypeWhenSet() {
        // given
        final Device device = Device.builder()
                .devicetype(3)
                .build();
        final var wurflDevice = mockDesktop(); // device type 2
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);
        // then
        assertThat(result.getDevicetype()).isEqualTo(3); // unchanged
    }

    @Test
    void updateShouldHandleTabletDeviceType() {
        // given
        final Device device = Device.builder().build();
        final var wurflDevice = mockTablet();
        // when
        final Device result = target.update(device, wurflDevice, staticCaps, virtualCaps, true);
        // then
        assertThat(result.getDevicetype()).isEqualTo(5);
    }

    @Test
    void updateShouldAddWurflPropertyToExtIfMissingAndPreserveExistingProperties() {
        // given
        final ExtDevice existingExt = ExtDevice.empty();
        existingExt.addProperty("someProperty", TextNode.valueOf("value"));
        final Device device = Device.builder()
                .ext(existingExt)
                .build();

        final var wurflDevice = WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone();
        final Set<String> staticCaps = Set.of("brand_name");
        final Set<String> virtualCaps = Set.of("advertised_device_os");

        final OrtbDeviceUpdater updater = new OrtbDeviceUpdater();

        // when
        final Device result = updater.update(device, wurflDevice, staticCaps, virtualCaps, true);

        // then
        final ExtDevice resultExt = result.getExt();
        assertThat(resultExt).isNotNull();
        assertThat(resultExt.getProperty("someProperty").textValue()).isEqualTo("value");
        assertThat(resultExt.getProperty("wurfl")).isNotNull();

    }

}
