package org.prebid.server.geolocation;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class CountryCodeMapperTest {

    private CountryCodeMapper countryCodeMapper;

    @Before
    public void setUp() {
        countryCodeMapper = new CountryCodeMapper("GB, GBR\nUK, GBR");
    }

    @Test
    public void creationShouldThrowErrorInvalidResourceFile() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new CountryCodeMapper("invalid_resouce"));
    }

    @Test
    public void mapToAlpha3ShouldCorrectlyMapAllAlpha2Codes() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("UK")).isEqualTo("GBR");
        assertThat(countryCodeMapper.mapToAlpha3("GB")).isEqualTo("GBR");
    }

    @Test
    public void mapToAlpha3ShouldTolerateInvalidCaseAlpha2Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("uK")).isEqualTo("GBR");
        assertThat(countryCodeMapper.mapToAlpha3("Gb")).isEqualTo("GBR");
    }

    @Test
    public void mapToAlpha3ShouldReturnNullOnEmptyAlpha2Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha3("")).isNull();
    }

    @Test
    public void mapToAlpha2ShouldCorrectlyMapLatestAlpha3Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("GBR")).isEqualTo("UK");
    }

    @Test
    public void mapToAlpha2ShouldTolerateInvalidCaseAlpha3Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("GbR")).isEqualTo("UK");
    }

    @Test
    public void mapToAlpha2ShouldReturnNullOnEmptyAlpha3Code() {
        // when and then
        assertThat(countryCodeMapper.mapToAlpha2("")).isNull();
    }
}
