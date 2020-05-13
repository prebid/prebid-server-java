package org.prebid.server.auction;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

public class ImplicitParametersExtractorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final PublicSuffixList psl = new PublicSuffixListFactory().build();

    private ImplicitParametersExtractor extractor;
    @Mock
    private HttpServerRequest httpRequest;

    @Before
    public void setUp() {
        // minimal request
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        extractor = new ImplicitParametersExtractor(psl);
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeader() {
        // given
        httpRequest.headers().set(HttpUtil.REFERER_HEADER, "http://example.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRefererHeaderIfUrlOverrideParamBlank() {
        // given
        httpRequest.headers().set(HttpUtil.REFERER_HEADER, "http://example.com");
        given(httpRequest.getParam("url_override")).willReturn("");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererFromRequestParamIfUrlOverrideParamExists() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("http://exampleoverrride.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://exampleoverrride.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfUrlOverrideParamDoesNotContainScheme() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("example.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
    }

    @Test
    public void refererFromShouldReturnRefererWithHttpSchemeIfRefererHeaderDoesNotContainScheme() {
        // given
        httpRequest.headers().set(HttpUtil.REFERER_HEADER, "example.com");

        // when and then
        assertThat(extractor.refererFrom(httpRequest)).isEqualTo("http://example.com");
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
    public void ipFromShouldReturnSingleIpFromXForwardedFor() {
        // given
        httpRequest.headers().set("X-Forwarded-For", " 193.168.144.1 ");

        // when and then
        assertThat(extractor.ipFrom(httpRequest)).isEqualTo("193.168.144.1");
    }

    @Test
    public void ipFromShouldReturnFirstValidIpFromXForwardedFor() {
        // given
        httpRequest.headers().set("X-Forwarded-For", "192.168.144.1 , 192.168.244.2 , 193.168.44.1 ");

        // when and then
        assertThat(extractor.ipFrom(httpRequest)).isEqualTo("193.168.44.1");
    }

    @Test
    public void ipFromShouldReturnRemoteAddress() {
        // given
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "193.168.244.1"));

        // when and then
        assertThat(extractor.ipFrom(httpRequest)).isEqualTo("193.168.244.1");
    }

    @Test
    public void ipFromShouldReturnIpFromXRealIP() {
        // given
        httpRequest.headers().set("X-Real-IP", " 192.168.44.1 ");

        // when and then
        assertThat(extractor.ipFrom(httpRequest)).isEqualTo("192.168.44.1");
    }

    @Test
    public void uaFromShouldReturnUaFromUserAgentHeader() {
        // given
        httpRequest.headers().set(HttpUtil.USER_AGENT_HEADER, " user agent ");

        // when and then
        assertThat(extractor.uaFrom(httpRequest)).isEqualTo("user agent");
    }

    @Test
    public void secureFromShouldReturnOneIfXForwardedProtoIsHttps() {
        // given
        httpRequest.headers().set("X-Forwarded-Proto", "https");

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnOneIfConnectedViaSSL() {
        // given
        given(httpRequest.scheme()).willReturn("https");

        // when and then
        assertThat(extractor.secureFrom(httpRequest)).isEqualTo(1);
    }

    @Test
    public void secureFromShouldReturnNull() {
        assertThat(extractor.secureFrom(httpRequest)).isNull();
    }
}
