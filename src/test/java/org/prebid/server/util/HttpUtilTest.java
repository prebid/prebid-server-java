package org.prebid.server.util;

import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;

import java.util.Map;
import java.util.function.Consumer;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class HttpUtilTest {

    @Mock(strictness = LENIENT)
    private RoutingContext routingContext;
    @Mock(strictness = LENIENT)
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
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
    public void validateDomainNameShouldReturnExpectedDomainName() {
        // when and then
        assertThat(HttpUtil.validateDomainName("example.com")).isEqualTo("example.com");
        assertThat(HttpUtil.validateDomainName("sub.domain-example.com")).isEqualTo("sub.domain-example.com");
        assertThat(HttpUtil.validateDomainName("127.0.0.1")).isEqualTo("127.0.0.1");
        assertThat(HttpUtil.validateDomainName("example.com:8080")).isEqualTo("example.com:8080");
        assertThat(HttpUtil.validateDomainName("")).isEqualTo("");
    }

    @Test
    public void validateDomainNameShouldFailOnNull() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validateDomainName(null))
                .withMessage("Domain name is null");
    }

    @Test
    public void validateDomainNameShouldFailOnInvalidCharacters() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validateDomainName("example.com/path"))
                .withMessage("Domain name example.com/path contains invalid characters");

        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validateDomainName("example@com"))
                .withMessage("Domain name example@com contains invalid characters");

        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validateDomainName("example.com:port"))
                .withMessage("Domain name example.com:port contains invalid characters");
    }

    @Test
    public void validatePathSegmentShouldReturnExpectedPathSegment() {
        // when and then
        assertThat(HttpUtil.validatePathSegment("path")).isEqualTo("path");
        assertThat(HttpUtil.validatePathSegment("path/to/resource")).isEqualTo("path/to/resource");
        assertThat(HttpUtil.validatePathSegment(".")).isEqualTo(".");
    }

    @Test
    public void validatePathSegmentShouldFailOnForbiddenCharacter() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validatePathSegment("path?query"))
                .withMessage("Path segment path?query contains forbidden character ?");

        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validatePathSegment("path#fragment"))
                .withMessage("Path segment path#fragment contains forbidden character #");

        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validatePathSegment("path\\segment"))
                .withMessage("Path segment path\\segment contains forbidden character \\");
    }

    @Test
    public void validatePathSegmentShouldFailOnDoubleSlash() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validatePathSegment("path//segment"))
                .withMessage("Path segment path//segment contains forbidden sequence //");
    }

    @Test
    public void validatePathSegmentShouldFailOnSegmentContainingTwoDots() {
        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validatePathSegment("path/../segment"))
                .withMessage("Path segment path/../segment contains forbidden segment ..");

        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> HttpUtil.validatePathSegment(".."))
                .withMessage("Path segment .. contains forbidden segment ..");
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
        given(httpRequest.cookies()).willReturn(singleton(Cookie.cookie("name", "value")));

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
}
