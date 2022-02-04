package org.prebid.server.auction.privacycontextfactory;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class AmpPrivacyContextFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private PrivacyExtractor privacyExtractor;
    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock
    private IpAddressHelper ipAddressHelper;
    @Mock
    private CountryCodeMapper countryCodeMapper;

    private AmpPrivacyContextFactory ampPrivacyContextFactory;

    @Before
    public void setUp() {
        ampPrivacyContextFactory = new AmpPrivacyContextFactory(
                privacyExtractor, tcfDefinerService, ipAddressHelper, countryCodeMapper);
    }

    @Test
    public void contextFromShouldExtractInitialPrivacy() {
        // given
        final Privacy emptyPrivacy = Privacy.of("", "", Ccpa.EMPTY, null);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(emptyPrivacy);
        given(privacyExtractor.toValidPrivacy(any(), any(), any(), any(), any()))
                .willReturn(emptyPrivacy);

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final AuctionContext auctionContext = givenAuctionContext(
                contextBuilder -> contextBuilder.httpRequest(givenHttpRequestContext("invalid")));

        // when
        ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        verify(privacyExtractor).validPrivacyFrom(any(), anyList());
    }

    @Test
    public void contextFromShouldAddTcfExtractionWarningsToAuctionDebugWarnings() {
        // given
        final Privacy emptyPrivacy = Privacy.of("", "", Ccpa.EMPTY, null);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(emptyPrivacy);
        given(privacyExtractor.toValidPrivacy(any(), any(), any(), any(), any()))
                .willReturn(emptyPrivacy);

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.builder().warnings(singletonList("Error")).build()));

        final AuctionContext auctionContext = givenAuctionContext(
                contextBuilder -> contextBuilder.httpRequest(givenHttpRequestContext(null)));

        // when
        ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        assertThat(auctionContext.getDebugWarnings()).containsExactly("Error");
    }

    @Test
    public void contextFromShouldRemoveConsentStringAndEmitErrorOnInvalidConsentTypeParam() {
        // given
        final Privacy privacy = Privacy.of("1", "consent_string", Ccpa.EMPTY, null);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(privacy);

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final AuctionContext auctionContext = givenAuctionContext(
                contextBuilder -> contextBuilder.httpRequest(givenHttpRequestContext("invalid")));

        // when
        final Future<PrivacyContext> result = ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        assertThat(result.result().getPrivacy()).isEqualTo(privacy.withoutConsent());
        assertThat(auctionContext.getPrebidErrors()).containsExactly("Invalid consent_type param passed");
    }

    @Test
    public void contextFromShouldNotRemoveConsentStringOnEmptyConsentTypeParam() {
        // given
        final Privacy privacy = Privacy.of("1", "consent_string", Ccpa.EMPTY, null);
        given(privacyExtractor.validPrivacyFrom(any(), any())).willReturn(privacy);

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final AuctionContext auctionContext = givenAuctionContext(
                contextBuilder -> contextBuilder.httpRequest(givenHttpRequestContext(null)));

        // when
        final Future<PrivacyContext> result = ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        assertThat(result.result().getPrivacy()).isEqualTo(privacy);
        assertThat(auctionContext.getPrebidErrors()).isEmpty();
    }

    @Test
    public void contextFromShouldRemoveConsentStringAndEmitErrorOnTcf1ConsentTypeParam() {
        // given
        final Privacy privacy = Privacy.of("1", "consent_string", Ccpa.EMPTY, null);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(privacy);

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final AuctionContext auctionContext = givenAuctionContext(
                contextBuilder -> contextBuilder.httpRequest(givenHttpRequestContext("1")));

        // when
        final Future<PrivacyContext> result = ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        assertThat(result.result().getPrivacy()).isEqualTo(privacy.withoutConsent());
        assertThat(auctionContext.getPrebidErrors()).containsExactly("Consent type tcfV1 is no longer supported");
    }

    private static AuctionContext givenAuctionContext(
            UnaryOperator<AuctionContext.AuctionContextBuilder> auctionContextCustomizer) {

        final AuctionContext.AuctionContextBuilder defaultAuctionContextBuilder =
                AuctionContext.builder()
                        .httpRequest(givenHttpRequestContext(null))
                        .debugWarnings(new ArrayList<>())
                        .account(Account.builder().build())
                        .prebidErrors(new ArrayList<>())
                        .bidRequest(givenBidRequest());

        return auctionContextCustomizer.apply(defaultAuctionContextBuilder).build();
    }

    private static BidRequest givenBidRequest() {
        return BidRequest.builder().device(Device.builder().build()).build();
    }

    private static HttpRequestContext givenHttpRequestContext(String consentType) {
        final CaseInsensitiveMultiMap.Builder queryParamBuilder =
                CaseInsensitiveMultiMap.builder();

        if (StringUtils.isNotEmpty(consentType)) {
            queryParamBuilder.add("consent_type", consentType);
        }

        return HttpRequestContext.builder()
                .queryParams(queryParamBuilder.build())
                .build();
    }
}
