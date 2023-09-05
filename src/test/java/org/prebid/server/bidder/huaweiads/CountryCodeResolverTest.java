package org.prebid.server.bidder.huaweiads;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class CountryCodeResolverTest {

    @Test
    public void resolveShouldReturnFirst2LettersCodeFromDeviceWhenDeviceCountryHasMoreThan2Letters() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("deviceCountry").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("userCountry").build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

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
                .user(User.builder().geo(Geo.builder().country("userCountry").build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("UA");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromDeviceWhenDeviceCountryHasAlpha2CountryCode() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("UK").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("userCountry").build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("UK");
    }

    @Test
    public void resolveShouldReturnFirst2LettersCodeFromUserWhenUserCountryHasMoreThan2LettersAndDeviceHasAlpha2Code() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("U").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("userCountry").build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("us");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromUserWhenUserCountryHasAlpha3CountryCodeAndDeviceCountryIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("UKR").build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("UA");
    }

    @Test
    public void resolveShouldReturnAlpha2CodeFromUserWhenUserCountryHasAlpha2CountryCodeAndDeviceCountryIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().country("").build())
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().geo(Geo.builder().country("UK").build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).hasValue("UK");
    }

    @Test
    public void resolveShouldReturnFirst2LettersCodeFromMccWhenUserAndDeviceGeoIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .mccmnc("202-304")
                        .build())
                .user(User.builder().build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

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
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).isEmpty();
    }

    @Test
    public void resolveShouldReturnEmptyWhenUserAndDeviceAreNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .geo(Geo.builder().build())
                        .mccmnc("100-304")
                        .build())
                .user(User.builder().geo(Geo.builder().build()).build())
                .build();

        // when
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

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
        final Optional<String> actual = CountryCodeResolver.resolve(bidRequest);

        //then
        assertThat(actual).isEmpty();
    }

}
