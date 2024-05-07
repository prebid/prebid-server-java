package org.prebid.server.auction.privacy.contextfactory;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.execution.Timeout;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class SetuidPrivacyContextFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PrivacyExtractor privacyExtractor;
    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock
    private ImplicitParametersExtractor implicitParametersExtractor;
    @Mock
    private IpAddressHelper ipAddressHelper;

    private SetuidPrivacyContextFactory target;

    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private Timeout timeout;

    @Before
    public void setUp() {
        target = new SetuidPrivacyContextFactory(
                privacyExtractor,
                tcfDefinerService,
                implicitParametersExtractor,
                ipAddressHelper);
    }

    @Test
    public void contextFromShouldReturnExpectedPrivacy() {
        // given
        givenImplicitParametersResolver(emptyList());

        final Privacy privacy = Privacy.builder().build();
        given(privacyExtractor.validPrivacyFromSetuidRequest(any())).willReturn(privacy);
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final Account account = Account.builder().build();

        // when
        final PrivacyContext result = target.contextFrom(httpRequest, account, timeout).result();

        // then
        assertThat(result)
                .extracting(PrivacyContext::getPrivacy)
                .isSameAs(privacy);
    }

    @Test
    public void contextFromShouldUseRequestIp() {
        // given
        givenImplicitParametersResolver(singletonList("0.0.0.0"));

        given(ipAddressHelper.toIpAddress(eq("0.0.0.0")))
                .willReturn(IpAddress.of("ip", IpAddress.IP.v4));
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final Account account = Account.builder().build();

        // when
        target.contextFrom(httpRequest, account, timeout);

        // then
        verify(tcfDefinerService).resolveTcfContext(any(), eq("ip"), any(), any(), any(), any());
    }

    @Test
    public void contextFromShouldReturnExpectedTcfContext() {
        // given
        givenImplicitParametersResolver(emptyList());

        final TcfContext tcfContext = TcfContext.empty();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final Account account = Account.builder().build();

        // when
        final PrivacyContext result = target.contextFrom(httpRequest, account, timeout).result();

        // then
        assertThat(result)
                .extracting(PrivacyContext::getTcfContext)
                .isSameAs(tcfContext);
    }

    private void givenImplicitParametersResolver(List<String> ips) {
        given(httpRequest.remoteAddress()).willReturn(SocketAddress.inetSocketAddress(0, "host"));
        given(implicitParametersExtractor.ipFrom(Mockito.<MultiMap>any(), eq("host"))).willReturn(ips);
    }
}
