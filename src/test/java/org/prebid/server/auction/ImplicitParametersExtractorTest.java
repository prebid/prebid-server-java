package org.prebid.server.auction;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.util.HttpUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ImplicitParametersExtractorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final PublicSuffixList psl = new PublicSuffixListFactory().build();

    private ImplicitParametersExtractor extractor;

    @Before
    public void setUp() {
        extractor = new ImplicitParametersExtractor(psl);
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeader() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add(HttpUtil.REFERER_HEADER, "http://example.com")
                        .build())
                .queryParams(CaseInsensitiveMultiMap.empty())
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeaderIfUrlOverrideParamBlank() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add(HttpUtil.REFERER_HEADER, "http://example.com")
                        .build())
                .queryParams(CaseInsensitiveMultiMap.builder()
                        .add("url_override", "")
                        .build())
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRequestParamIfUrlOverrideParamExists() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.builder()
                        .add("url_override", "http://exampleoverrride.com")
                        .build())
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://exampleoverrride.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfUrlOverrideParamDoesNotContainScheme() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.builder()
                        .add("url_override", "example.com")
                        .build())
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfRefererHeaderDoesNotContainScheme() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add(HttpUtil.REFERER_HEADER, "example.com")
                        .build())
                .queryParams(CaseInsensitiveMultiMap.empty())
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void domainFromShouldFailIfHostIsNull() {
        assertThatCode(() -> extractor.domainFrom(null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Host is not defined or can not be derived from request");
    }

    @Test
    public void domainFromShouldFailIfDomainCouldNotBeDerivedFromHost() {
        assertThatCode(() -> extractor.domainFrom("domain"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot derive eTLD+1 for host domain");
    }

    @Test
    public void domainFromShouldDeriveDomainFromHost() {
        assertThat(extractor.domainFrom("example.com")).isEqualTo("example.com");
    }

    @Test
    public void domainFromShouldDeriveDomainFromHostWithSubdomain() {
        assertThat(extractor.domainFrom("subdomain.example.com")).isEqualTo("example.com");
    }

    @Test
    public void ipFromShouldReturnIpFromHeadersAndRemoteAddress() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("True-Client-IP", "192.168.144.1 ")
                .add("X-Forwarded-For", "192.168.144.2 , 192.168.144.3 ")
                .add("X-Real-IP", "192.168.144.4 ")
                .build();
        final String remoteHost = "192.168.144.5";

        // when and then
        assertThat(extractor.ipFrom(headers, remoteHost)).containsExactly(
                "192.168.144.1", "192.168.144.2", "192.168.144.3", "192.168.144.4", remoteHost);
    }

    @Test
    public void ipFromShouldNotReturnNullsAndEmptyValues() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("X-Real-IP", " ")
                .build();
        final String remoteHost = "192.168.144.5";

        // when and then
        assertThat(extractor.ipFrom(headers, remoteHost)).containsExactly("192.168.144.5");
    }

    @Test
    public void uaFromShouldReturnUaFromUserAgentHeader() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add(HttpUtil.USER_AGENT_HEADER, " user agent ")
                        .build())
                .build();

        // when and then
        assertThat(extractor.uaFrom(httpRequest)).isEqualTo("user agent");
    }

    @Test
    public void secureFromShouldReturnOneIfXForwardedProtoIsHttps() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add("X-Forwarded-Proto", "https")
                        .build())
                .build();

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnOneIfConnectedViaSSL() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.empty())
                .scheme("https")
                .build();

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnNull() {
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.empty())
                .build();
        assertThat(extractor.secureFrom(httpRequest)).isNull();
    }
}
