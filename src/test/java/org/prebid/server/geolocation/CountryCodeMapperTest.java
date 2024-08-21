package org.prebid.server.geolocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class CountryCodeMapperTest {

    private CountryCodeMapper target;

    @BeforeEach
    public void setUp() {
        target = new CountryCodeMapper("GB, GBR\nUK, GBR", "402, UA");
    }

    @Test
    public void creationShouldThrowErrorInvalidCountryCodesResourceFile() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new CountryCodeMapper("invalid_resource", "401, UA"));
    }

    @Test
    public void creationShouldThrowErrorInvalidMccCountryCodesResourceFile() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> new CountryCodeMapper("UA, UKR", "invalid_resource"));
    }

    @Test
    public void mapToAlpha3ShouldCorrectlyMapAllAlpha2Codes() {
        // when and then
        assertThat(target.mapToAlpha3("UK")).isEqualTo("GBR");
        assertThat(target.mapToAlpha3("GB")).isEqualTo("GBR");
    }

    @Test
    public void mapToAlpha3ShouldTolerateInvalidCaseAlpha2Code() {
        // when and then
        assertThat(target.mapToAlpha3("uK")).isEqualTo("GBR");
        assertThat(target.mapToAlpha3("Gb")).isEqualTo("GBR");
    }

    @Test
    public void mapToAlpha3ShouldReturnNullOnEmptyAlpha2Code() {
        // when and then
        assertThat(target.mapToAlpha3("")).isNull();
    }

    @Test
    public void mapToAlpha2ShouldCorrectlyMapLatestAlpha3Code() {
        // when and then
        assertThat(target.mapToAlpha2("GBR")).isEqualTo("UK");
    }

    @Test
    public void mapToAlpha2ShouldTolerateInvalidCaseAlpha3Code() {
        // when and then
        assertThat(target.mapToAlpha2("GbR")).isEqualTo("UK");
    }

    @Test
    public void mapToAlpha2ShouldReturnNullOnEmptyAlpha3Code() {
        // when and then
        assertThat(target.mapToAlpha2("")).isNull();
    }

    @Test
    public void mapMccToAlpha2ShouldCorrectlyMapAlpha2Code() {
        // when and then
        assertThat(target.mapMccToAlpha2("402")).isEqualTo("UA");
    }

    @Test
    public void mapMccToAlpha2ShouldReturnNullOnEmptyAlpha2Code() {
        // when and then
        assertThat(target.mapMccToAlpha2("")).isNull();
    }
}
