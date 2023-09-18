package org.prebid.server.geolocation;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class CountryCodeMapperTest {

    private CountryCodeMapper countryCodeMapper;

    @Before
    public void setUp() {
        countryCodeMapper = new CountryCodeMapper("UA, UKR");
    }

    @Test
    public void creationShouldThrowErrorInvalidResourceFile() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new CountryCodeMapper("invalid_resouce"));
    }

    @Test
    public void mapToAlpha3ShouldCorrectlyMapAlpha2Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("UA")).isEqualTo("UKR");
    }

    @Test
    public void mapToAlpha3ShouldTolerateInvalidCaseAlpha2Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("uA")).isEqualTo("UKR");
    }

    @Test
    public void mapToAlpha3ShouldReturnNullOnEmptyAlpha2Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("")).isNull();
    }

    @Test
    public void mapToAlpha2ShouldCorrectlyMapAlpha3Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("UKR")).isEqualTo("UA");
    }

    @Test
    public void mapToAlpha2ShouldTolerateInvalidCaseAlpha3Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("uKr")).isEqualTo("UA");
    }

    @Test
    public void mapToAlpha2ShouldReturnNullOnEmptyAlpha3Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("")).isNull();
    }
}
