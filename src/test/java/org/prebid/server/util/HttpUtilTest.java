package org.prebid.server.util;

import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class HttpUtilTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.response()).willReturn(httpResponse);
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
    public void getHostFromUrlShouldReturnDomain() {
        // given and when
        final String host = HttpUtil.getHostFromUrl("http://www.domain.com/ad");

        // then
        assertThat(host).isEqualTo("www.domain.com");
    }

    @Test
    public void getHostFromUrlShouldReturnNullIfUrlIsMalformed() {
        // given and when
        final String host = HttpUtil.getHostFromUrl("www.domain.com");

        // then
        assertThat(host).isNull();
    }

    @Test
    public void cookiesAsMapShouldReturnExpectedResult() {
        // given
        given(routingContext.cookieMap()).willReturn(singletonMap("name", Cookie.cookie("name", "value")));

        // when
        final Map<String, String> cookies = HttpUtil.cookiesAsMap(routingContext);

        // then
        assertThat(cookies).hasSize(1)
                .containsOnly(entry("name", "value"));
    }

    @Test
    public void cookiesAsMapFromRequestShouldReturnExpectedResult() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add(HttpHeaders.COOKIE, Cookie.cookie("name", "value").encode())
                        .build())
                .build();

        // when
        final Map<String, String> cookies = HttpUtil.cookiesAsMap(httpRequest);

        // then
        assertThat(cookies).hasSize(1)
                .containsOnly(entry("name", "value"));
    }

    @Test
    public void toSetCookieHeaderValueShouldReturnExpectedString() {
        // given
        final Cookie cookie = Cookie.cookie("cookie", "value")
                .setPath("/")
                .setDomain("domain.com");

        // when
        final String setCookieHeaderValue = HttpUtil.toSetCookieHeaderValue(cookie);

        // then
        assertThat(setCookieHeaderValue).isEqualTo("cookie=value; Path=/; Domain=domain.com; SameSite=None; Secure");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void executeSafelyShouldSkipResponseIfClientClosedConnection() {
        // given
        given(httpResponse.closed()).willReturn(true);
        final Consumer<HttpServerResponse> responseConsumer = mock(Consumer.class);

        // when
        HttpUtil.executeSafely(routingContext, "endpoint", responseConsumer);

        // then
        verifyNoMoreInteractions(responseConsumer);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void executeSafelyShouldRespondToClient() {
        // given
        final Consumer<HttpServerResponse> responseConsumer = mock(Consumer.class);

        // when
        final boolean result = HttpUtil.executeSafely(routingContext, "endpoint", responseConsumer);

        // then
        verify(responseConsumer).accept(eq(httpResponse));
        assertThat(result).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void executeSafelyShouldReturnFalseIfResponseFailed() {
        // given
        final Consumer<HttpServerResponse> responseConsumer = mock(Consumer.class);
        doThrow(new RuntimeException("error")).when(responseConsumer).accept(any());

        // when
        final boolean result = HttpUtil.executeSafely(routingContext, "endpoint", responseConsumer);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void getDateFromHeaderShouldReturnDate() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("date-header",
                "2019-11-04T13:31:24.365+02:00[Europe/Kiev]");

        // when
        final ZonedDateTime result = HttpUtil.getDateFromHeader(headers, "date-header");

        // then
        assertThat(result).isEqualTo(ZonedDateTime.parse("2019-11-04T13:31:24.365+02:00[Europe/Kiev]"));
    }

    @Test
    public void getDateFromHeaderShouldReturnNullWhenHeaderWasNotFound() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        // when
        final ZonedDateTime result = HttpUtil.getDateFromHeader(headers, "not-exist");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getDateFromHeaderShouldThrowExceptionWhenHeaderHasIncorrectFormat() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("date-header", "invalid");

        // when and then
        assertThatThrownBy(() -> HttpUtil.getDateFromHeader(headers, "date-header"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("date-header header is not compatible to ISO-8601 format: invalid");
    }

    @Test
    public void getDateFromHeaderShouldReturnDateFromHeaders() {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("date-header",
                "2019-11-04T13:31:24.365+02:00[Europe/Kiev]");

        // when
        final ZonedDateTime result = HttpUtil.getDateFromHeader(headers, "date-header");

        // then
        assertThat(result).isEqualTo(ZonedDateTime.parse("2019-11-04T13:31:24.365+02:00[Europe/Kiev]"));
    }
}
