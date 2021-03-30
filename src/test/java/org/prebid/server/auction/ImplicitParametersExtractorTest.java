package org.prebid.server.auction;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ImplicitParametersExtractorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final PublicSuffixList psl = new PublicSuffixListFactory().build();

    private ImplicitParametersExtractor extractor;

    private HttpRequestWrapper httpRequest;

    @Before
    public void setUp() {
        // minimal request
        httpRequest = HttpRequestWrapper.builder()
                .queryParams(new CaseInsensitiveHeaders())
                .headers(new CaseInsensitiveHeaders())
                .build();

        extractor = new ImplicitParametersExtractor(psl);
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeader() {
        // given
        httpRequest.getHeaders().set(HttpUtil.REFERER_HEADER, "http://example.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeaderIfUrlOverrideParamBlank() {
        // given
        httpRequest.getHeaders().set(HttpUtil.REFERER_HEADER, "http://example.com");
        httpRequest.getQueryParams().set("url_override", "");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRequestParamIfUrlOverrideParamExists() {
        // given
        httpRequest.getQueryParams().set("url_override", "http://exampleoverrride.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://exampleoverrride.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfUrlOverrideParamDoesNotContainScheme() {
        // given
        httpRequest.getQueryParams().set("url_override", "example.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfRefererHeaderDoesNotContainScheme() {
        // given
        httpRequest.getHeaders().set(HttpUtil.REFERER_HEADER, "example.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void domainFromShouldFailIfUrlIsMissing() {
        assertThatCode(() -> extractor.domainFrom(null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid URL 'null': null")
                .hasCauseInstanceOf(MalformedURLException.class);
    }

    @Test
    public void domainFromShouldFailIfUrlCouldNotBeParsed() {
        assertThatCode(() -> extractor.domainFrom("httpP://non_an_url"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid URL 'httpP://non_an_url': unknown protocol: httpp")
                .hasCauseInstanceOf(MalformedURLException.class);
    }

    @Test
    public void domainFromShouldFailIfUrlDoesNotContainHost() {
        assertThatCode(() -> extractor.domainFrom("http:/path"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Host not found from URL 'http:/path'");
    }

    @Test
    public void domainFromShouldFailIfDomainCouldNotBeDerivedFromUrl() {
        assertThatCode(() -> extractor.domainFrom("http://domain"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid URL 'domain': cannot derive eTLD+1 for domain domain");
    }

    @Test
    public void domainFromShouldDeriveDomainFromUrl() {
        assertThat(extractor.domainFrom("http://example.com")).isEqualTo("example.com");
    }

    @Test
    public void ipFromShouldReturnIpFromHeadersAndRemoteAddress() {
        // given
        httpRequest = HttpRequestWrapper.builder()
                .headers(new CaseInsensitiveHeaders())
                .remoteHost("192.168.144.5")
                .build();
        httpRequest.getHeaders().set("True-Client-IP", "192.168.144.1 ");
        httpRequest.getHeaders().set("X-Forwarded-For", "192.168.144.2 , 192.168.144.3 ");
        httpRequest.getHeaders().set("X-Real-IP", "192.168.144.4 ");

        // when and then
        assertThat(extractor.ipFrom(httpRequest)).containsExactly(
                "192.168.144.1", "192.168.144.2", "192.168.144.3", "192.168.144.4", "192.168.144.5");
    }

    @Test
    public void ipFromShouldNotReturnNullsAndEmptyValues() {
        // given
        httpRequest = HttpRequestWrapper.builder()
                .headers(new CaseInsensitiveHeaders())
                .remoteHost("192.168.144.5")
                .build();
        httpRequest.getHeaders().set("X-Real-IP", " ");

        // when and then
        assertThat(extractor.ipFrom(httpRequest)).containsExactly("192.168.144.5");
    }

    @Test
    public void uaFromShouldReturnUaFromUserAgentHeader() {
        // given
        httpRequest.getHeaders().set(HttpUtil.USER_AGENT_HEADER, " user agent ");

        // when and then
        assertThat(extractor.uaFrom(httpRequest)).isEqualTo("user agent");
    }

    @Test
    public void secureFromShouldReturnOneIfXForwardedProtoIsHttps() {
        // given
        httpRequest.getHeaders().set("X-Forwarded-Proto", "https");

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnOneIfConnectedViaSSL() {
        // given
        httpRequest = HttpRequestWrapper.builder()
                .headers(MultiMap.caseInsensitiveMultiMap())
                .scheme("https")
                .build();

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnNull() {
        assertThat(extractor.secureFrom(httpRequest)).isNull();
    }
}
