package org.prebid.server.auction.privacycontextfactory;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.assertion.FutureAssertion;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.gdpr.model.TCStringEmpty;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.privacy.model.PrivacyDebugLog;
import org.prebid.server.privacy.model.PrivacyResult;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class AuctionPrivacyContextFactoryTest extends VertxTest {

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

    private AuctionPrivacyContextFactory auctionPrivacyContextFactory;

    @Before
    public void setUp() {
        auctionPrivacyContextFactory = new AuctionPrivacyContextFactory(
                privacyExtractor, tcfDefinerService, ipAddressHelper, countryCodeMapper);
    }

    @Test
    public void contextFromBidRequestShouldReturnTcfContextForCoppa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.of(1, null))
                .build();

        final TcfContext tcfContext = TcfContext.builder()
                .gdpr("1")
                .consentString("consent")
                .consent(TCStringEmpty.create())
                .warnings(Collections.emptyList())
                .build();

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final Privacy originPrivacy = Privacy.of(null, null, Ccpa.EMPTY, 1);
        final Privacy validPrivacy = Privacy.of("", "", Ccpa.EMPTY, 1);
        givenPrivacyExtractorWithValidAndOriginPrivacyResults(originPrivacy, validPrivacy);

        final AuctionContext auctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(bidRequest)
                .account(Account.empty("account"))
                .prebidErrors(new ArrayList<>())
                .build();

        // when
        final Future<PrivacyContext> privacyContext = auctionPrivacyContextFactory.contextFrom(auctionContext);

        // then
        FutureAssertion.assertThat(privacyContext).succeededWith(
                PrivacyContext.of(
                        validPrivacy,
                        tcfContext,
                        null,
                        PrivacyDebugLog.from(originPrivacy, validPrivacy, tcfContext)));
    }

    @Test
    public void contextFromBidRequestShouldReturnTcfContext() {
        // given
        final String referer = "Referer";
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.of(null, ExtRegs.of(1, "1YYY")))
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .consent("consent")
                                .build())
                        .build())
                .site(Site.builder().ref(referer).build())
                .build();

        final TcfContext tcfContext = TcfContext.builder()
                .gdpr("1")
                .consentString("consent")
                .consent(TCStringEmpty.create())
                .warnings(Collections.emptyList())
                .build();

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final Privacy originPrivacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), null);
        final Privacy validPrivacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        givenPrivacyExtractorWithValidAndOriginPrivacyResults(originPrivacy, validPrivacy);

        final String accountId = "account";
        final MetricName requestType = MetricName.openrtb2web;

        final AuctionContext auctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(bidRequest)
                .account(Account.empty(accountId))
                .requestTypeMetric(requestType)
                .prebidErrors(new ArrayList<>())
                .build();

        // when
        final Future<PrivacyContext> privacyContext = auctionPrivacyContextFactory.contextFrom(auctionContext);

        // then
        FutureAssertion.assertThat(privacyContext).succeededWith(
                PrivacyContext.of(
                        validPrivacy,
                        tcfContext,
                        null,
                        PrivacyDebugLog.from(originPrivacy, validPrivacy, tcfContext)));

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(requestType, referer, accountId);
        verify(tcfDefinerService).resolveTcfContext(
                eq(validPrivacy), isNull(), isNull(), isNull(), same(requestType),
                eq(expectedRequestLogInfo), isNull());
    }

    @Test
    public void contextFromBidRequestShouldReturnTcfContextWithIpMasked() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.of(null, ExtRegs.of(1, "1YYY")))
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .consent("consent")
                                .build())
                        .build())
                .device(Device.builder()
                        .lmt(1)
                        .ip("ip")
                        .build())
                .build();

        given(ipAddressHelper.maskIpv4(any())).willReturn("ip-masked");

        final TcfContext tcfContext = TcfContext.builder()
                .gdpr("1")
                .consentString("consent")
                .consent(TCStringEmpty.create())
                .warnings(Collections.emptyList())
                .ipAddress("ip-masked")
                .inEea(false)
                .geoInfo(GeoInfo.builder().vendor("v").country("ua").build())
                .build();

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final Privacy originPrivacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), null);
        final Privacy validPrivacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        givenPrivacyExtractorWithValidAndOriginPrivacyResults(originPrivacy, validPrivacy);

        final AuctionContext auctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(bidRequest)
                .account(Account.empty("account"))
                .requestTypeMetric(MetricName.openrtb2web)
                .prebidErrors(new ArrayList<>())
                .build();

        // when
        final Future<PrivacyContext> privacyContext = auctionPrivacyContextFactory.contextFrom(auctionContext);

        // then
        FutureAssertion.assertThat(privacyContext).succeededWith(
                PrivacyContext.of(
                        validPrivacy,
                        tcfContext,
                        "ip-masked",
                        PrivacyDebugLog.from(originPrivacy, validPrivacy, tcfContext)));

        verify(tcfDefinerService).resolveTcfContext(
                eq(validPrivacy), isNull(), eq("ip-masked"), isNull(), same(MetricName.openrtb2web), any(), isNull());
    }

    @Test
    public void contextFromShouldAddPrivacyExtractionErrorsToAuctionPrebidErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.of(null, null))
                .user(User.builder().build())
                .device(Device.builder().build())
                .build();

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.empty()));

        final Privacy privacy = Privacy.of("0", "", Ccpa.EMPTY, 0);
        given(privacyExtractor.extractPrivacyFrom(any(BidRequest.class)))
                .willReturn(
                        PrivacyResult.builder()
                                .originPrivacy(privacy)
                                .validPrivacy(privacy)
                                .errors(Collections.singletonList("Error"))
                                .build());


        final AuctionContext auctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(bidRequest)
                .account(Account.empty("account"))
                .requestTypeMetric(MetricName.openrtb2web)
                .prebidErrors(new ArrayList<>())
                .build();

        // when
        auctionPrivacyContextFactory.contextFrom(auctionContext);

        // then
        assertThat(auctionContext.getPrebidErrors()).containsExactly("Error");
    }

    @Test
    public void contextFromBidRequestShouldCallResolveTcfContextWithIpv6AnonymizedWhenIpNotPresentAndLmtIsOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .lmt(1)
                        .ipv6("ipv6")
                        .build())
                .build();

        given(ipAddressHelper.anonymizeIpv6(any())).willReturn("ip-masked");
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.builder().build()));
        givenPrivacyExtractorWithDefaultValidAndOriginPrivacyResults();

        final AuctionContext auctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(bidRequest)
                .account(Account.empty("account"))
                .prebidErrors(new ArrayList<>())
                .build();

        // when
        auctionPrivacyContextFactory.contextFrom(auctionContext);

        // then
        verify(tcfDefinerService).resolveTcfContext(any(), any(), eq("ip-masked"), any(), any(), any(), any());
    }

    @Test
    public void contextFromBidRequestShouldCallResolveTcfContextWithIpv6WhenIpv4NotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .ipv6("ipv6")
                        .build())
                .build();

        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfContext.builder().build()));
        givenPrivacyExtractorWithDefaultValidAndOriginPrivacyResults();

        final AuctionContext auctionContext = AuctionContext.builder()
                .debugWarnings(new ArrayList<>())
                .bidRequest(bidRequest)
                .account(Account.empty("account"))
                .prebidErrors(new ArrayList<>())
                .build();

        // when
        auctionPrivacyContextFactory.contextFrom(auctionContext);

        // then
        verify(tcfDefinerService).resolveTcfContext(any(), any(), eq("ipv6"), any(), any(), any(), any());
    }

    private void givenPrivacyExtractorWithDefaultValidAndOriginPrivacyResults() {
        final Privacy privacy = Privacy.of("0", "", Ccpa.EMPTY, 0);
        givenPrivacyExtractorWithValidAndOriginPrivacyResults(privacy, privacy);
    }

    private void givenPrivacyExtractorWithValidAndOriginPrivacyResults(Privacy originPrivacy, Privacy validPrivacy) {
        given(privacyExtractor.extractPrivacyFrom(any(BidRequest.class)))
                .willReturn(
                        PrivacyResult.builder()
                                .originPrivacy(originPrivacy)
                                .validPrivacy(validPrivacy)
                                .errors(Collections.emptyList())
                                .build());
    }
}
