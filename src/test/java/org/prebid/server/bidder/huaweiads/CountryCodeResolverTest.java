package org.prebid.server.bidder.huaweiads;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.geolocation.CountryCodeMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class CountryCodeResolverTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CountryCodeMapper countryCodeMapper;

    private CountryCodeResolver target;

    @Before
    public void before() {
        target = new CountryCodeResolver(countryCodeMapper);
        given(countryCodeMapper.mapToAlpha2("UKR")).willReturn("UA");
    }

    @Test
    public void resolveShouldReturnCodeFromDeviceAsIsWhenDeviceCountryHasAlpha2CountryCode() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("de").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("us").build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("de");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromDeviceWhenDeviceCountryHasAlpha3CountryCode() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("UKR").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("us").build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("UA");
    }

    @Test
    public void resolveShouldReturnCodeFromUserAsIsWhenDeviceHasInvalidAlpha3CodeAndUserHasAlpha2Code() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("UUU").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("us").build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("us");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromUserWhenDeviceHasInvalidCodeAndUserHasAlpha3Code() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("UKR").build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("UA");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromMccWhenUserAndDeviceGeoAreEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("GR");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromMccWhenUserAndDeviceHasInvalidAlpha3Codes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("AAA").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("BBB").build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("GR");
    }

    @Test
    public void resolveShouldReturnEmptyWhenUserAndDeviceHasEmptyCountryAndMccCanNotBeResolvedIntoCode() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().build())
                        .mccmnc("100-304")
                        .build())
                .user(User.builder().geo(Geo.builder().build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).isEmpty();
    }

    @Test
    public void resolveShouldReturnEmptyWhenUserAndDeviceAreNullAndMccCanNotBeResolvedIntoCode() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().build())
                        .mccmnc("100-304")
                        .build())
                .user(User.builder().geo(Geo.builder().build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).isEmpty();
    }

    @Test
    public void resolveShouldReturnEmptyWhenUserAndDeviceGeoHasBlankCountryAndMccIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().geo(Geo.builder().country("").build()).mccmnc("").build())
                .user(User.builder().geo(Geo.builder().country("").build()).build())
                .build();

        // when
        final Optional<String> actual = target.resolve(bidRequest);

        //then
        assertThat(actual).isEmpty();
    }

}
