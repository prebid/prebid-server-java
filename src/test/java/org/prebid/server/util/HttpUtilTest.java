package org.prebid.server.util;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Map;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

public class HttpUtilTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

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
        final String url = HttpUtil.encodeUrl("//domain.org/query-string?a=1");

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
        assertThat(headers).isEmpty();
    }

    @Test
    public void addHeaderIfValueIsNotEmptyShouldNotAddHeaderIfValueIsNull() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        // when
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "header", null);

        // then
        assertThat(headers).isEmpty();
    }

    @Test
    public void getDomainFromUrlShouldReturnDomain() {
        // given and when
        final String domain = HttpUtil.getDomainFromUrl("http://rubicon.com/ad");

        // then
        assertThat(domain).isEqualTo("rubicon.com");
    }

    @Test
    public void getDomainFromUrlShouldReturnNullIfUrlIsMalformed() {
        // given and when
        final String domain = HttpUtil.getDomainFromUrl("rubicon.com");

        // then
        assertThat(domain).isNull();
    }

    @Test
    public void cookiesAsMapShouldReturnExpectedResult() {
        // given
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie("name", "value")));

        // when
        final Map<String, String> cookies = HttpUtil.cookiesAsMap(routingContext);

        // then
        assertThat(cookies).hasSize(1)
                .containsOnly(entry("name", "value"));
    }

    @Test
    public void toSetCookieHeaderValueShouldReturnExpectedString() {
        // given
        final Cookie cookie = Cookie.cookie("cookie", "value")
                .setPath("/")
                .setDomain("rubicon.com");

        // when
        final String setCookieHeaderValue = HttpUtil.toSetCookieHeaderValue(cookie);

        // then
        assertThat(setCookieHeaderValue).isEqualTo("cookie=value; Path=/; Domain=rubicon.com; SameSite=none");
    }
}
