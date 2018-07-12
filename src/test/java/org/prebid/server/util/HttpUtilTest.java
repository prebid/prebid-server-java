package org.prebid.server.util;

import io.vertx.core.MultiMap;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class HttpUtilTest {

    @Test
    public void isSafariShouldReturnTrue() {
        assertThat(HttpUtil.isSafari("Useragent with Safari browser and AppleWebKit built-in.")).isTrue();
    }

    @Test
    public void isSafariShouldReturnFalse() {
        assertThat(HttpUtil.isSafari("Useragent with Safari browser but Chromium forked by.")).isFalse();
    }

    @Test
    public void validateUrlShouldFailOnInvalidUrl() {
        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> HttpUtil.validateUrl("invalid_url"))
                .isInstanceOf(IllegalArgumentException.class)
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void validateUrlShouldReturnExpectedUrl() {
        // when
        final String url = HttpUtil.validateUrl("http://domain.org/query-string?a=1");

        // then
        assertThat(url).isNotNull();
        assertThat(url).isEqualTo("http://domain.org/query-string?a=1");
    }

    @Test
    public void encodeUrlShouldReturnExpectedValue() {
        // when
        final String url = HttpUtil.encodeUrl("//domain.org/%s", "query-string?a=1");

        // then
        assertThat(url).isNotNull();
        assertThat(url).isEqualTo("%2F%2Fdomain.org%2Fquery-string%3Fa%3D1");
    }

    @Test
    public void addHeaderIfValueIsNotEmptyShouldAddHeaderIfValueIsNotEmptyAndNotNull() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        // when
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "header", "value");

        // then
        assertThat(headers)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("header", "value"));
    }

    @Test
    public void addHeaderIfValueIsNotEmptyShouldNotAddHeaderIfValueIsEmpty() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        // when
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "header", "");

        // then
        assertThat(headers).hasSize(0);
    }

    @Test
    public void addHeaderIfValueIsNotEmptyShouldNotAddHeaderIfValueIsNull() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        // when
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "header", null);

        // then
        assertThat(headers).hasSize(0);
    }
}
