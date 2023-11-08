package org.prebid.server.bidder.huaweiads;

import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.bidder.huaweiads.model.request.CellInfo;
import org.prebid.server.bidder.huaweiads.model.request.Network;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HuaweiNetworkBuilderTest {

    private final HuaweiNetworkBuilder target = new HuaweiNetworkBuilder();

    @Test
    public void buildShouldReturnNullWhenDeviceIsNull() {
        // given & when
        final Network actual = target.build(null);

        // then
        assertThat(actual).isNull();
    }

    @Test
    public void buildShouldBuildNetworkWithDefaultValuesWhenDeviceIsEmpty() {
        // given
        final Device device = Device.builder().build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(0)
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNetworkWithCarrier0AndEmptyCellInfoWhenDeviceHasInvalidMccMnc() {
        // given
        final Device device = Device.builder()
                .mccmnc("invalid")
                .build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(0)
                .carrier(0)
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNetworkWithCarrier2AndCellInfoWhenDeviceHasValidMccMnc() {
        // given
        final Device device = Device.builder()
                .mccmnc("460-00")
                .build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(0)
                .carrier(2)
                .cellInfo(List.of(CellInfo.of("460", "00")))
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNetworkWithCarrier1AndCellInfoWhenDeviceHasValidMccMnc() {
        // given
        final Device device = Device.builder()
                .mccmnc("460-01")
                .build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(0)
                .carrier(1)
                .cellInfo(List.of(CellInfo.of("460", "01")))
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNetworkWithCarrier3AndCellInfoWhenDeviceHasValidMccMnc() {
        // given
        final Device device = Device.builder()
                .mccmnc("460-11")
                .build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(0)
                .carrier(3)
                .cellInfo(List.of(CellInfo.of("460", "11")))
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNetworkWithCarrier99AndCellInfoWhenDeviceHasValidMccMnc() {
        // given
        final Device device = Device.builder()
                .mccmnc("460-12")
                .build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(0)
                .carrier(99)
                .cellInfo(List.of(CellInfo.of("460", "12")))
                .build();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldBuildNetworkWithTypeWhenDeviceHasConnectionType() {
        // given
        final Device device = Device.builder()
                .mccmnc("460-12")
                .connectiontype(3)
                .build();

        // when
        final Network actual = target.build(device);

        // then
        final Network expected = Network.builder()
                .cellInfo(Collections.emptyList())
                .type(3)
                .carrier(99)
                .cellInfo(List.of(CellInfo.of("460", "12")))
                .build();
        assertThat(actual).isEqualTo(expected);
    }

}
