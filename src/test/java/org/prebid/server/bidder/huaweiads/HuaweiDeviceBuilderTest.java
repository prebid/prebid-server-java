package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.User;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.request.Device;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HuaweiDeviceBuilderTest extends VertxTest {

    private final HuaweiDeviceBuilder target = new HuaweiDeviceBuilder(jacksonMapper);

    @Test
    public void buildShouldFailWhenUserAndDeviceIsAbsent() {
        // given & when & then
        assertThatThrownBy(() -> target.build(null, null, "DE"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("getDeviceID: openRTBRequest.User.Ext is nil and device.Gaid is not specified.");
    }

    @Test
    public void buildShouldFailWhenUserExtAndDeviceIfaIsAbsent() {
        // given
        final com.iab.openrtb.request.Device device = com.iab.openrtb.request.Device.builder().ifa("").build();
        final User user = User.builder().ext(null).build();

        // when & then
        assertThatThrownBy(() -> target.build(device, user, "DE"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("getDeviceID: openRTBRequest.User.Ext is nil and device.Gaid is not specified.");
    }

    @Test
    public void buildShouldBuildDeviceWhenUserExtIsAbsentAndOnlyDeviceIfaIsPresent() {
        // given
        final com.iab.openrtb.request.Device device = com.iab.openrtb.request.Device.builder().ifa("ifa").build();
        final User user = User.builder().ext(null).build();

        // when
        final Device actual = target.build(device, user, "DE");

        // then
        final Device expected = Device.builder()
                .gaid("ifa")
                .model("HUAWEI")
                .localeCountry("DE")
                .belongCountry("DE")
                .build();

        assertThat(actual).usingRecursiveComparison().ignoringFields("clientTime").isEqualTo(expected);
        assertThat(actual.getClientTime()).isNotNull();
    }

    @Test
    public void buildShouldFailWhenUserExtIsPresentButHasEmptyData() {
        // given
        final User user = User.builder().ext(ExtUser.builder().build()).build();

        // when & then
        assertThatThrownBy(() -> target.build(null, user, "DE"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("getDeviceID: Imei, Oaid, Gaid are all empty.");
    }

    @Test
    public void buildShouldFailWhenUserExtDataHasEmptyImeiGaidAndOaid() {
        // given
        final ObjectNode data = mapper.createObjectNode()
                .<ObjectNode>set("oaid", mapper.createArrayNode())
                .<ObjectNode>set("gaid", mapper.createArrayNode())
                .set("imei", mapper.createArrayNode());
        final User user = User.builder().ext(ExtUser.builder().data(data).build()).build();

        // when & then
        assertThatThrownBy(() -> target.build(null, user, "DE"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("getDeviceID: Imei, Oaid, Gaid are all empty.");
    }

    @Test
    public void buildShouldBuildDeviceWhenUserExtDataHasGaidOaidImeiAndClientTime() {
        // given
        final ObjectNode data = mapper.createObjectNode()
                .<ObjectNode>set("clientTime", mapper.createArrayNode()
                        .add("2022-02-24 04:00:05.000+0200")
                        .add("2022-02-24 04:00:05.000+0300"))
                .<ObjectNode>set("oaid", mapper.createArrayNode().add("oaid1").add("oaid2"))
                .<ObjectNode>set("gaid", mapper.createArrayNode().add("gaid1").add("gaid2"))
                .set("imei", mapper.createArrayNode().add("imei1").add("imei2"));
        final User user = User.builder().ext(ExtUser.builder().data(data).build()).build();

        // when
        final Device actual = target.build(null, user, "DE");

        // then
        final Device expected = Device.builder()
                .gaid("gaid1")
                .imei("imei1")
                .oaid("oaid1")
                .clientTime("2022-02-24 04:00:05.000+0200")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildDeviceWithIfaWhenUserExtDataHasOaidImeiButClientTimeAndGaidAreEmpty() {
        // given
        final com.iab.openrtb.request.Device device = com.iab.openrtb.request.Device.builder().ifa("ifa").build();
        final ObjectNode data = mapper.createObjectNode()
                .<ObjectNode>set("clientTime", mapper.createArrayNode())
                .<ObjectNode>set("oaid", mapper.createArrayNode().add("oaid1").add("oaid2"))
                .<ObjectNode>set("gaid", mapper.createArrayNode())
                .set("imei", mapper.createArrayNode().add("imei1").add("imei2"));
        final User user = User.builder().ext(ExtUser.builder().data(data).build()).build();

        // when
        final Device actual = target.build(device, user, "DE");

        // then
        final Device expected = Device.builder()
                .gaid("gaid1")
                .imei("imei1")
                .oaid("oaid1")
                .gaid("ifa")
                .model("HUAWEI")
                .localeCountry("DE")
                .belongCountry("DE")
                .build();

        assertThat(actual).usingRecursiveComparison().ignoringFields("clientTime").isEqualTo(expected);
        assertThat(actual.getClientTime()).isNotNull();
    }

    @Test
    public void buildShouldBuildDeviceWithAllFieldsWhenDeviceHasAllFieldsAndUserExtDataIsPresent() {
        // given
        final com.iab.openrtb.request.Device device = com.iab.openrtb.request.Device.builder()
                .ifa("ifa")
                .devicetype(2)
                .ua("user agent")
                .os("os")
                .osv("osv")
                .make("make")
                .model("model")
                .h(100)
                .w(200)
                .language("lang")
                .pxratio(BigDecimal.TEN)
                .ip("ip")
                .dnt(2)
                .build();
        final ObjectNode data = mapper.createObjectNode()
                .<ObjectNode>set("clientTime", mapper.createArrayNode()
                        .add("2022-02-24 04:00:05.000+0200")
                        .add("2022-02-24 04:00:05.000+0300"))
                .<ObjectNode>set("oaid", mapper.createArrayNode().add("oaid1").add("oaid2"))
                .<ObjectNode>set("gaid", mapper.createArrayNode())
                .set("imei", mapper.createArrayNode().add("imei1").add("imei2"));
        final User user = User.builder().ext(ExtUser.builder().data(data).build()).build();

        // when
        final Device actual = target.build(device, user, "DE");

        // then
        final Device expected = Device.builder()
                .gaid("gaid1")
                .imei("imei1")
                .oaid("oaid1")
                .clientTime("2022-02-24 04:00:05.000+0200")
                .gaid("ifa")
                .model("HUAWEI")
                .localeCountry("DE")
                .belongCountry("DE")
                .type(2)
                .userAgent("user agent")
                .os("os")
                .version("osv")
                .maker("make")
                .model("model")
                .height(100)
                .width(200)
                .language("lang")
                .pxratio(BigDecimal.TEN)
                .ip("ip")
                .isTrackingEnabled("-1")
                .gaidTrackingEnabled("-1")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

}
