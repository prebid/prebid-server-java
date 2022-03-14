package org.prebid.server.auction.privacycontextfactory;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
                        .bidRequest(givenBidRequest(identity()));

        return auctionContextCustomizer.apply(defaultAuctionContextBuilder).build();
    }

    @Test
    public void contextFromShouldMaskIpV4WhenCoppaEqualsToOneAndIpV4Present() {
        // given
        final Privacy privacy = Privacy.of("1", "consent_string", Ccpa.of("1YYY"), 1);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(privacy);

        given(ipAddressHelper.maskIpv4(anyString()))
                .willReturn("maskedIpV4");
        given(ipAddressHelper.anonymizeIpv6(anyString()))
                .willReturn("maskedIpV6");

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.device(Device.builder().ip("ip").build()));

        final AuctionContext auctionContext = givenAuctionContext(auctionContextBuilder ->
                auctionContextBuilder
                        .httpRequest(givenHttpRequestContext("invalid"))
                        .bidRequest(bidRequest));

        // when
        ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        verify(ipAddressHelper).maskIpv4(anyString());
    }

    @Test
    public void contextFromShouldMaskIpV6WhenCoppaEqualsToOneAndIpV6Present() {
        // given
        final Privacy privacy = Privacy.of("1", "consent_string", Ccpa.of("1YYY"), 1);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(privacy);

        given(ipAddressHelper.maskIpv4(anyString()))
                .willReturn("maskedIpV4");
        given(ipAddressHelper.anonymizeIpv6(anyString()))
                .willReturn("maskedIpV6");

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.device(Device.builder().ipv6("ipV6").build()));

        final AuctionContext auctionContext = givenAuctionContext(auctionContextBuilder ->
                auctionContextBuilder
                        .httpRequest(givenHttpRequestContext("invalid"))
                        .bidRequest(bidRequest));

        // when
        ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        verify(ipAddressHelper).anonymizeIpv6(anyString());
    }

    @Test
    public void contextFromShouldAddRefUrlWhenPresentAndRequestTypeIsWeb() {
        // given
        final Privacy privacy = Privacy.of("1", "consent_string", Ccpa.EMPTY, 0);
        given(privacyExtractor.validPrivacyFrom(any(), any()))
                .willReturn(privacy);

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.site(Site.builder().ref("refUrl").build()));

        final AuctionContext auctionContext = givenAuctionContext(auctionContextBuilder ->
                auctionContextBuilder
                        .requestTypeMetric(MetricName.openrtb2web)
                        .httpRequest(givenHttpRequestContext("invalid"))
                        .bidRequest(bidRequest));

        // when
        ampPrivacyContextFactory.contextFrom(auctionContext);

        // then
        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(MetricName.openrtb2web, "refUrl", null);
        verify(tcfDefinerService)
                .resolveTcfContext(any(), any(), any(), any(), any(), eq(expectedRequestLogInfo), any());
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()).build();
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
