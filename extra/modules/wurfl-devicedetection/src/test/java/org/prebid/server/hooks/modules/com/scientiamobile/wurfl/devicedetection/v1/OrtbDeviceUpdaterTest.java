package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import org.mockito.Mock;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import com.iab.openrtb.request.BidRequest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class OrtbDeviceUpdaterTest {

    private Set<String> staticCaps;
    private Set<String> virtualCaps;
    private JacksonMapper mapper;

    @Mock(strictness = LENIENT)
    private AuctionRequestPayload payload;

    @Mock(strictness = LENIENT)
    private com.scientiamobile.wurfl.core.Device wurflDevice;

    @BeforeEach
    void setUp() {
        staticCaps = Set.of("ajax_support_javascript", "brand_name", "density_class",
                "is_connected_tv", "is_ott", "is_tablet", "model_name", "resolution_height", "resolution_width");
        virtualCaps = Set.of("advertised_device_os", "advertised_device_os_version",
                "is_full_desktop", "pixel_density");
        mapper = new JacksonMapper(ObjectMapperProvider.mapper());
    }

    @Test
    public void updateShouldUpdateDeviceMakeWhenOriginalIsEmpty() {
        // given
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getCapabilityAsBool("ajax_support_javascript")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(true);
        given(wurflDevice.getCapabilityAsBool("is_tablet")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);
        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getMake()).isEqualTo("Apple");
        assertThat(resultDevice.getDevicetype()).isEqualTo(1);
    }

    @Test
    public void updateShouldNotUpdateDeviceMakeWhenOriginalExists() {
        // given
        final Device device = Device.builder().make("Samsung").build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getCapability("ajax_support_javascript")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_mobile")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_phone")).willReturn("true");
        given(wurflDevice.getCapability("is_tablet")).willReturn("false");
        given(wurflDevice.getVirtualCapability("is_full_desktop")).willReturn("false");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getMake()).isEqualTo("Samsung");
    }

    @Test
    public void updateShouldNotUpdateDeviceMakeWhenOriginalBigIntegerExists() {
        // given
        final Device device = Device.builder().make("Apple").pxratio(new BigDecimal("1.0")).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getCapability("ajax_support_javascript")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_mobile")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_phone")).willReturn("true");
        given(wurflDevice.getCapability("is_tablet")).willReturn("false");
        given(wurflDevice.getVirtualCapability("is_full_desktop")).willReturn("false");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getMake()).isEqualTo("Apple");
        assertThat(resultDevice.getPxratio()).isEqualTo("1.0");
    }

    @Test
    public void updateShouldUpdateDeviceModelWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getCapability("ajax_support_javascript")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_mobile")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_phone")).willReturn("true");
        given(wurflDevice.getCapability("is_tablet")).willReturn("false");
        given(wurflDevice.getVirtualCapability("is_full_desktop")).willReturn("false");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getModel()).isEqualTo("iPhone");
    }

    @Test
    public void updateShouldUpdateDeviceOsWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getVirtualCapability("advertised_device_os")).willReturn("iOS");
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getOs()).isEqualTo("iOS");
    }

    @Test
    public void updateShouldUpdateResolutionWhenOriginalIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getCapabilityAsBool("ajax_support_javascript")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_tablet")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        given(wurflDevice.getCapabilityAsInt("resolution_width")).willReturn(3200);
        given(wurflDevice.getCapabilityAsInt("resolution_height")).willReturn(1440);
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);
        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getW()).isEqualTo(3200);
        assertThat(resultDevice.getH()).isEqualTo(1440);
    }

    @Test
    public void updateShouldHandleJavascriptSupport() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapabilityAsBool("ajax_support_javascript")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(true);
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getJs()).isEqualTo(1);
    }

    @Test
    public void updateShouldHandleOttDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapabilityAsBool("is_ott")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(false);
        given(wurflDevice.getCapabilityAsBool("is_tablet")).willReturn(false);
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);
        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(7);
    }

    @Test
    public void updateShouldReturnDeviceOtherMobileWhenMobileIsNotPhoneOrTablet() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(false);
        given(wurflDevice.getCapabilityAsBool("is_tablet")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        given(wurflDevice.getVirtualCapability("advertised_device_os")).willReturn("TestOs");
        given(wurflDevice.getVirtualCapability("advertised_device_os_version")).willReturn("1.0");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(6);
    }

    @Test
    public void updateShouldReturnNullWhenMobileTypeIsUnknown() {
        // given
        given(wurflDevice.getVirtualCapability("is_mobile")).willReturn("false");
        given(wurflDevice.getVirtualCapability("is_phone")).willReturn("false");
        given(wurflDevice.getCapability("is_tablet")).willReturn("false");
        given(wurflDevice.getVirtualCapability("is_full_desktop")).willReturn("false");
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        given(payload.bidRequest()).willReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isNull();
    }

    @Test
    public void updateShouldHandlePersonalComputerDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(false);
        given(wurflDevice.getCapabilityAsBool("is_tablet")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(true);
        given(wurflDevice.getVirtualCapability("advertised_device_os")).willReturn("Windows");
        given(wurflDevice.getVirtualCapability("advertised_device_os_version")).willReturn("10");
        given(wurflDevice.getVirtualCapability("form_factor")).willReturn("Desktop");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        given(payload.bidRequest()).willReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(2);
    }

    @Test
    public void updateShouldHandleConnectedTvDeviceType() {
        // given
        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapabilityAsBool("is_connected_tv")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_tablet")).willReturn(false);
        given(wurflDevice.getVirtualCapability("advertised_device_os")).willReturn("WebOS");
        given(wurflDevice.getVirtualCapability("advertised_device_os_version")).willReturn("4");
        given(wurflDevice.getCapabilityAsBool("is_connected_tv")).willReturn(true);
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        given(payload.bidRequest()).willReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(3);
    }

    @Test
    public void updateShouldNotUpdateDeviceTypeWhenSet() {
        // given
        final Device device = Device.builder()
                .devicetype(3)
                .build();
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_tablet")).willReturn(false); // device type 2
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        // when
        given(payload.bidRequest()).willReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);
        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(3); // unchanged
    }

    @Test
    public void updateShouldHandleTabletDeviceType() {
        // given
        given(wurflDevice.getCapabilityAsBool("is_tablet")).willReturn(true);
        given(wurflDevice.getVirtualCapabilityAsBool("is_full_desktop")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_mobile")).willReturn(false);
        given(wurflDevice.getVirtualCapabilityAsBool("is_phone")).willReturn(false);
        given(wurflDevice.getCapability("brand_name")).willReturn("Samsung");
        given(wurflDevice.getCapability("model_name")).willReturn("Galaxy Tab S9+");

        final Device device = Device.builder().build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);
        given(payload.bidRequest()).willReturn(bidRequest);
        // when
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        assertThat(resultDevice.getDevicetype()).isEqualTo(5);
    }

    @Test
    public void updateShouldAddWurflPropertyToExtIfMissingAndPreserveExistingProperties() {
        // given
        final ExtDevice existingExt = ExtDevice.empty();
        existingExt.addProperty("someProperty", TextNode.valueOf("value"));
        final Device device = Device.builder()
                .ext(existingExt)
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(wurflDevice.getCapability("ajax_support_javascript")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_mobile")).willReturn("true");
        given(wurflDevice.getVirtualCapability("is_phone")).willReturn("true");
        given(wurflDevice.getCapability("is_tablet")).willReturn("false");
        given(wurflDevice.getVirtualCapability("is_full_desktop")).willReturn("false");
        final Set<String> staticCaps = Set.of("brand_name");
        final Set<String> virtualCaps = Set.of("advertised_device_os");
        final OrtbDeviceUpdater target = new OrtbDeviceUpdater(wurflDevice, staticCaps, virtualCaps, true, mapper);

        // when
        given(payload.bidRequest()).willReturn(bidRequest);
        final AuctionRequestPayload result = target.apply(payload);

        // then
        final Device resultDevice = result.bidRequest().getDevice();
        final ExtDevice resultExt = resultDevice.getExt();
        assertThat(resultExt).isNotNull();
        assertThat(resultExt.getProperty("someProperty").textValue()).isEqualTo("value");
        assertThat(resultExt.getProperty("wurfl")).isNotNull();

    }
}
