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
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.util.HttpUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ImplicitParametersExtractorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final PublicSuffixList psl = new PublicSuffixListFactory().build();

    private ImplicitParametersExtractor extractor;

    private HttpRequestContext httpRequest;

    @Before
    public void setUp() {
        // minimal request
        httpRequest = HttpRequestContext.builder()
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
        final MultiMap headers = new CaseInsensitiveHeaders()
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
        final MultiMap headers = new CaseInsensitiveHeaders()
                .set("X-Real-IP", " ");
        final String remoteHost = "192.168.144.5";

        // when and then
        assertThat(extractor.ipFrom(headers, remoteHost)).containsExactly("192.168.144.5");
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
        httpRequest = HttpRequestContext.builder()
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
