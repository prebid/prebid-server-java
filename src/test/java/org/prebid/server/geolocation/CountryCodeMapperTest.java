package org.prebid.server.geolocation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class CountryCodeMapperTest {

    @Test
    public void creationShouldThrowErrorInvalidResourceFile() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CountryCodeMapper("invalid_resouce"));
    }

    @Test
    public void mapToAlpha3ShouldCorrectlyMapAlpha2Code() {
        // given
        CountryCodeMapper countryCodeMapper = new CountryCodeMapper("UA, UKR");

        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("UA")).isEqualTo("UKR");
    }

    @Test
    public void mapToAlpha3ShouldTolerateInvalidCaseAlpha2Code() {
        // given
        CountryCodeMapper countryCodeMapper = new CountryCodeMapper("UA, UKR");

        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("uA")).isEqualTo("UKR");
    }

    @Test
    public void mapToAlpha3ShouldReturnNullOnEmptyAlpha2Code() {
        // given
        CountryCodeMapper countryCodeMapper = new CountryCodeMapper("UA, UKR");

        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("")).isNull();
    }

    @Test
    public void mapToAlpha2ShouldCorrectlyMapAlpha3Code() {
        // given
        CountryCodeMapper countryCodeMapper = new CountryCodeMapper("UA, UKR");

        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("UKR")).isEqualTo("UA");
    }

    @Test
    public void mapToAlpha2ShouldTolerateInvalidCaseAlpha3Code() {
        // given
        CountryCodeMapper countryCodeMapper = new CountryCodeMapper("UA, UKR");

        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("uKr")).isEqualTo("UA");
    }

    @Test
    public void mapToAlpha2ShouldReturnNullOnEmptyAlpha3Code() {
        // given
        CountryCodeMapper countryCodeMapper = new CountryCodeMapper("UA, UKR");

        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("")).isNull();
    }
}
