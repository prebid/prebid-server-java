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
import org.prebid.server.model.MultiMap;
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
                .headers(CaseInsensitiveMultiMap.of()
                        .set(HttpUtil.REFERER_HEADER, "http://example.com"))
                .queryParams(CaseInsensitiveMultiMap.of())
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeaderIfUrlOverrideParamBlank() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.of()
                        .set(HttpUtil.REFERER_HEADER, "http://example.com"))
                .queryParams(CaseInsensitiveMultiMap.of()
                        .set("url_override", ""))
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRequestParamIfUrlOverrideParamExists() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.of()
                        .set("url_override", "http://exampleoverrride.com"))
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://exampleoverrride.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfUrlOverrideParamDoesNotContainScheme() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.of()
                        .set("url_override", "example.com"))
                .build();

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfRefererHeaderDoesNotContainScheme() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.of()
                        .set(HttpUtil.REFERER_HEADER, "example.com"))
                .queryParams(CaseInsensitiveMultiMap.of())
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
        final MultiMap headers = CaseInsensitiveMultiMap.of()
                .set("True-Client-IP", "192.168.144.1 ")
                .set("X-Forwarded-For", "192.168.144.2 , 192.168.144.3 ")
                .set("X-Real-IP", "192.168.144.4 ");
        final String remoteHost = "192.168.144.5";

        // when and then
        assertThat(extractor.ipFrom(headers, remoteHost)).containsExactly(
                "192.168.144.1", "192.168.144.2", "192.168.144.3", "192.168.144.4", remoteHost);
    }

    @Test
    public void ipFromShouldNotReturnNullsAndEmptyValues() {
        // given
        final MultiMap headers = CaseInsensitiveMultiMap.of()
                .set("X-Real-IP", " ");
        final String remoteHost = "192.168.144.5";

        // when and then
        assertThat(extractor.ipFrom(headers, remoteHost)).containsExactly("192.168.144.5");
    }

    @Test
    public void uaFromShouldReturnUaFromUserAgentHeader() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.of()
                        .set(HttpUtil.USER_AGENT_HEADER, " user agent "))
                .build();

        // when and then
        assertThat(extractor.uaFrom(httpRequest)).isEqualTo("user agent");
    }

    @Test
    public void secureFromShouldReturnOneIfXForwardedProtoIsHttps() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.of()
                        .set("X-Forwarded-Proto", "https"))
                .build();

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnOneIfConnectedViaSSL() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.of())
                .scheme("https")
                .build();

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnNull() {
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.of())
                .build();
        assertThat(extractor.secureFrom(httpRequest)).isNull();
    }
}
