package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import org.mockito.Mock;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import com.iab.openrtb.request.BidRequest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockConnectedTv;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockDesktop;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockMobileUndefinedDevice;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockOttDevice;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockTablet;
import static org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock.WURFLDeviceMockFactory.mockUnknownDevice;

@ExtendWith(MockitoExtension.class)
class OrtbDeviceUpdaterTest {

    private Set<String> staticCaps;
    private Set<String> virtualCaps;
    private JacksonMapper mapper;
    @Mock(strictness = LENIENT)
    private AuctionRequestPayload payload;

    @BeforeEach
    void setUp() {
        staticCaps = Set.of("ajax_support_javascript", "brand_name", "density_class",
                "is_connected_tv", "is_ott", "is_tablet", "model_name", "resolution_height", "resolution_width");
        virtualCaps = Set.of("advertised_device_os", "advertised_device_os_version",
                "is_full_desktop", "pixel_density");
        mapper = new JacksonMapper(ObjectMapperProvider.mapper());
    }

    @Test
    void updateShouldUpdateDeviceMakeWhenOriginalIsEmpty() {
        // given
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getMake()).isEqualTo("Apple");
        assertThat(resultDevice.getDevicetype()).isEqualTo(1);
    }

    @Test
    void updateShouldNotUpdateDeviceMakeWhenOriginalExists() {
        // given
        final Device device = Device.builder().make("Samsung").build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getMake()).isEqualTo("Samsung");
    }

    @Test
    void updateShouldNotUpdateDeviceMakeWhenOriginalBigIntegerExists() {
        // given
        final Device device = Device.builder().make("Apple").pxratio(new BigDecimal("1.0")).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getMake()).isEqualTo("Apple");
        assertThat(resultDevice.getPxratio()).isEqualTo("1.0");
    }

    @Test
    void updateShouldUpdateDeviceModelWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getModel()).isEqualTo("iPhone");
    }

    @Test
    void updateShouldUpdateDeviceOsWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getOs()).isEqualTo("iOS");
    }

    @Test
    void updateShouldUpdateResolutionWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getW()).isEqualTo(3200);
        assertThat(resultDevice.getH()).isEqualTo(1440);
    }

    @Test
    void updateShouldHandleJavascriptSupport() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockIPhone();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getJs()).isEqualTo(1);
    }

    @Test
    void updateShouldHandleOttDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockOttDevice();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(7);
    }

    @Test
    void updateShouldReturnDeviceOtherMobileWhenMobileIsNotPhoneOrTablet() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockMobileUndefinedDevice();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(6);
    }

    @Test
    void updateShouldReturnNullWhenMobileTypeIsUnknown() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockUnknownDevice();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isNull();
    }

    @Test
    void updateShouldHandlePersonalComputerDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockDesktop();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(2);
    }

    @Test
    void updateShouldHandleConnectedTvDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockConnectedTv();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(3);
    }

    @Test
    void updateShouldNotUpdateDeviceTypeWhenSet() {
        // given
        final Device device = Device.builder()
                .devicetype(3)
                .build();
        final var wurflDevice = mockDesktop(); // device type 2
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(3); // unchanged
    }

    @Test
    void updateShouldHandleTabletDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = mockTablet();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(5);
    }

    @Test
    void updateShouldAddWurflPropertyToExtIfMissingAndPreserveExistingProperties() {
        // given
        final ExtDevice existingExt = ExtDevice.empty();
        existingExt.addProperty("someProperty", TextNode.valueOf("value"));
        final Device device = Device.builder()
                .ext(existingExt)
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final var wurflDevice = WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone();
        final Set<String> staticCaps = Set.of("brand_name");
        final Set<String> virtualCaps = Set.of("advertised_device_os");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        when(payload.bidRequest()).thenReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        final ExtDevice resultExt = resultDevice.getExt();
        assertThat(resultExt).isNotNull();
        assertThat(resultExt.getProperty("someProperty").textValue()).isEqualTo("value");
        assertThat(resultExt.getProperty("wurfl")).isNotNull();

    }
}
