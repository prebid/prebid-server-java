package org.prebid.server.auction.privacy.enforcement.mask;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class UserFpdTcfMaskTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private IpAddressHelper ipAddressHelper;

    private UserFpdTcfMask target;

    @Before
    public void setUp() {
        target = new UserFpdTcfMask(ipAddressHelper);
    }

    @Test
    public void maskUserShouldReturnExpectedResultWhenFpdMasked() {
        // given
        final User user = User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .keywords("keywords")
                .kwarray(emptyList())
                .data(emptyList())
                .eids(emptyList())
                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                .build();

        // when
        final User result = target.maskUser(user, true, false, false);

        // then
        assertThat(result).isEqualTo(
                User.builder()
                        .eids(emptyList())
                        .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                        .build());
    }

    @Test
    public void maskUserShouldReturnExpectedResultWhenEidsMasked() {
        // given
        final User user = User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .keywords("keywords")
                .kwarray(emptyList())
                .data(emptyList())
                .eids(emptyList())
                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                .build();

        // when
        final User result = target.maskUser(user, false, true, false);

        // then
        assertThat(result).isEqualTo(
                User.builder()
                        .id("id")
                        .buyeruid("buyeruid")
                        .yob(1)
                        .gender("gender")
                        .keywords("keywords")
                        .kwarray(emptyList())
                        .data(emptyList())
                        .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                        .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                        .build());
    }

    @Test
    public void maskUserShouldReturnExpectedResultWhenGeoMasked() {
        // given
        final User user = User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .keywords("keywords")
                .kwarray(emptyList())
                .data(emptyList())
                .eids(emptyList())
                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).build())
                .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                .build();

        // when
        final User result = target.maskUser(user, false, false, true);

        // then
        assertThat(result).isEqualTo(
                User.builder()
                        .id("id")
                        .buyeruid("buyeruid")
                        .yob(1)
                        .gender("gender")
                        .keywords("keywords")
                        .kwarray(emptyList())
                        .data(emptyList())
                        .eids(emptyList())
                        .geo(Geo.builder().lon(-85.34F).lat(189.34F).build())
                        .ext(ExtUser.builder().data(mapper.createObjectNode()).build())
                        .build());
    }

    @Test
    public void maskDeviceShouldReturnExpectedResultWhenIpMasked() {
        // given
        given(ipAddressHelper.maskIpv4(any())).willReturn("ip4");
        given(ipAddressHelper.anonymizeIpv6(any())).willReturn("ip6");

        final Device device = Device.builder()
                .ip("192.168.0.10")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .geo(Geo.builder()
                        .lon(-85.34321F)
                        .lat(189.342323F)
                        .build())
                .ifa("ifa")
                .macsha1("macsha1")
                .macmd5("macmd5")
                .didsha1("didsha1")
                .didmd5("didmd5")
                .dpidsha1("dpidsha1")
                .dpidmd5("dpidmd5")
                .build();

        // when
        final Device result = target.maskDevice(device, true, false, false);

        // then
        assertThat(result).isEqualTo(
                Device.builder()
                        .ip("ip4")
                        .ipv6("ip6")
                        .geo(Geo.builder()
                                .lon(-85.34321F)
                                .lat(189.342323F)
                                .build())
                        .ifa("ifa")
                        .macsha1("macsha1")
                        .macmd5("macmd5")
                        .didsha1("didsha1")
                        .didmd5("didmd5")
                        .dpidsha1("dpidsha1")
                        .dpidmd5("dpidmd5")
                        .build());
    }

    @Test
    public void maskDeviceShouldReturnExpectedResultWhenGeoMasked() {
        // given
        given(ipAddressHelper.maskIpv4(any())).willReturn("ip4");
        given(ipAddressHelper.anonymizeIpv6(any())).willReturn("ip6");

        final Device device = Device.builder()
                .ip("192.168.0.10")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .geo(Geo.builder()
                        .lon(-85.34321F)
                        .lat(189.342323F)
                        .build())
                .ifa("ifa")
                .macsha1("macsha1")
                .macmd5("macmd5")
                .didsha1("didsha1")
                .didmd5("didmd5")
                .dpidsha1("dpidsha1")
                .dpidmd5("dpidmd5")
                .build();

        // when
        final Device result = target.maskDevice(device, false, true, false);

        // then
        assertThat(result).isEqualTo(
                Device.builder()
                        .ip("192.168.0.10")
                        .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                        .geo(Geo.builder()
                                .lon(-85.34F)
                                .lat(189.34F)
                                .build())
                        .ifa("ifa")
                        .macsha1("macsha1")
                        .macmd5("macmd5")
                        .didsha1("didsha1")
                        .didmd5("didmd5")
                        .dpidsha1("dpidsha1")
                        .dpidmd5("dpidmd5")
                        .build());
    }

    @Test
    public void maskDeviceShouldReturnExpectedResultWhenDeviceInfoMasked() {
        // given
        given(ipAddressHelper.maskIpv4(any())).willReturn("ip4");
        given(ipAddressHelper.anonymizeIpv6(any())).willReturn("ip6");

        final Device device = Device.builder()
                .ip("192.168.0.10")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .geo(Geo.builder()
                        .lon(-85.34321F)
                        .lat(189.342323F)
                        .build())
                .ifa("ifa")
                .macsha1("macsha1")
                .macmd5("macmd5")
                .didsha1("didsha1")
                .didmd5("didmd5")
                .dpidsha1("dpidsha1")
                .dpidmd5("dpidmd5")
                .build();

        // when
        final Device result = target.maskDevice(device, false, false, true);

        // then
        assertThat(result).isEqualTo(
                Device.builder()
                        .ip("192.168.0.10")
                        .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                        .geo(Geo.builder()
                                .lon(-85.34321F)
                                .lat(189.342323F)
                                .build())
                        .build());
    }
}
