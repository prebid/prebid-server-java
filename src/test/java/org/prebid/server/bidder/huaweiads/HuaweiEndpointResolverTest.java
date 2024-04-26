package org.prebid.server.bidder.huaweiads;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class HuaweiEndpointResolverTest {

    private static final String ENDPOINT_URL = "https://test-url.org/";
    private static final String CHINESE_ENDPOINT_URL = "https://test-url.org/china";
    private static final String EUROPEAN_ENDPOINT_URL = "https://test-url.org/europe";
    private static final String RUSSIAN_ENDPOINT_URL = "https://test-url.orc/russia";
    private static final String ASIAN_ENDPOINT_URL = "https://test-url.org/asian";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private HuaweiEndpointResolver target;

    @BeforeEach
    public void before() {
        target = new HuaweiEndpointResolver(
                ENDPOINT_URL,
                CHINESE_ENDPOINT_URL,
                RUSSIAN_ENDPOINT_URL,
                EUROPEAN_ENDPOINT_URL,
                ASIAN_ENDPOINT_URL,
                StringUtils.EMPTY);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiEndpointResolver(
                        "invalid_url",
                        CHINESE_ENDPOINT_URL,
                        RUSSIAN_ENDPOINT_URL,
                        EUROPEAN_ENDPOINT_URL,
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY));
    }

    @Test
    public void creationShouldFailOnInvalidChineseEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiEndpointResolver(
                        ENDPOINT_URL,
                        "invalid_url",
                        RUSSIAN_ENDPOINT_URL,
                        EUROPEAN_ENDPOINT_URL,
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY));
    }

    @Test
    public void creationShouldFailOnInvalidRussianEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiEndpointResolver(
                        ENDPOINT_URL,
                        CHINESE_ENDPOINT_URL,
                        "invalid_url",
                        EUROPEAN_ENDPOINT_URL,
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY));
    }

    @Test
    public void creationShouldFailOnInvalidEuropeanEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiEndpointResolver(
                        ENDPOINT_URL,
                        CHINESE_ENDPOINT_URL,
                        RUSSIAN_ENDPOINT_URL,
                        "invalid_url",
                        ASIAN_ENDPOINT_URL,
                        StringUtils.EMPTY));
    }

    @Test
    public void creationShouldFailOnInvalidAsianEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HuaweiEndpointResolver(
                        ENDPOINT_URL,
                        CHINESE_ENDPOINT_URL,
                        RUSSIAN_ENDPOINT_URL,
                        EUROPEAN_ENDPOINT_URL,
                        "invalid_url",
                        StringUtils.EMPTY));
    }

    @Test
    public void resolveShouldReturnDefaultEndpointWhenRequestTreatedAsFromCloseCountry() {
        // given
        target = new HuaweiEndpointResolver(
                ENDPOINT_URL,
                CHINESE_ENDPOINT_URL,
                RUSSIAN_ENDPOINT_URL,
                EUROPEAN_ENDPOINT_URL,
                ASIAN_ENDPOINT_URL,
                "1");

        // when
        final String actual = target.resolve("UA");

        // then
        assertThat(actual).isEqualTo(ENDPOINT_URL);
    }

    @Test
    public void resolveShouldReturnChineseEndpointWhenCountryIsChina() {
        // given & when
        final String actual = target.resolve("CN");

        // then
        assertThat(actual).isEqualTo(CHINESE_ENDPOINT_URL);
    }

    @Test
    public void resolveShouldReturnRussianEndpointWhenCountryIsrussia() {
        // given & when
        final String actual = target.resolve("RU");

        // then
        assertThat(actual).isEqualTo(RUSSIAN_ENDPOINT_URL);
    }

    @Test
    public void resolveShouldReturnEuropeanEndpointWhenCountryIsUkraine() {
        // given & when
        final String actual = target.resolve("UA");

        // then
        assertThat(actual).isEqualTo(EUROPEAN_ENDPOINT_URL);
    }

    @Test
    public void resolveShouldReturnAsianEndpointWhenCountryIsJapan() {
        // given & when
        final String actual = target.resolve("JP");

        // then
        assertThat(actual).isEqualTo(ASIAN_ENDPOINT_URL);
    }

}
