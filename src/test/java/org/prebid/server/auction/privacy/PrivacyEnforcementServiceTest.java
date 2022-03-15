package org.prebid.server.auction.privacy;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.assertion.FutureAssertion;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.privacy.PrivacyAnonymizationService;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.RequestLogInfo;
import org.prebid.server.privacy.gdpr.model.TCStringEmpty;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.privacy.model.PrivacyDebugLog;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCcpaConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class PrivacyEnforcementServiceTest extends VertxTest {

    private static final String BIDDER_NAME = "someBidder";
    private static final String BUYER_UID = "uidval";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    private PrivacyExtractor privacyExtractor;
    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock
    private ImplicitParametersExtractor implicitParametersExtractor;
    @Mock
    private IpAddressHelper ipAddressHelper;
    @Mock
    private Metrics metrics;
    @Mock
    private CountryCodeMapper countryCodeMapper;

    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private PrivacyAnonymizationService privacyAnonymizationService;

    @Mock
    private BidderAliases aliases;
    private Timeout timeout;

    @Before
    public void setUp() {
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, restrictDeviceAndUser()), null)));
        given(aliases.resolveBidder(anyString())).willAnswer(invocation -> invocation.getArgument(0));
        given(ipAddressHelper.maskIpv4(anyString())).willReturn("192.168.0.0");
        given(ipAddressHelper.anonymizeIpv6(eq("2001:0db8:85a3:0000:0000:8a2e:0370:7334")))
                .willReturn("2001:0db8:85a3:0000::");

        given(privacyAnonymizationService.maskCcpa(any(), any(), anyString()))
                .willReturn(BidderPrivacyResult.builder().build());
        given(privacyAnonymizationService.maskCoppa(any(), any(), anyString()))
                .willReturn(BidderPrivacyResult.builder().build());
        given(privacyAnonymizationService.maskTcf(any(), any(), any(), any(), any(), any()))
                .willReturn(BidderPrivacyResult.builder().build());

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);

        privacyExtractor = new PrivacyExtractor();

        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, false, false);
    }

    @Test
    public void contextFromSetuidRequestShouldReturnContext() {
        // given
        final HttpServerRequest httpRequest = mock(HttpServerRequest.class);
        given(httpRequest.getParam("gdpr")).willReturn("1");
        given(httpRequest.getParam("gdpr_consent")).willReturn("consent");
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        given(httpRequest.headers()).willReturn(headers);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        given(implicitParametersExtractor.ipFrom(eq(headers), eq("host"))).willReturn(singletonList("ip"));
        given(implicitParametersExtractor
                .ipFrom(any(CaseInsensitiveMultiMap.class), any())).willReturn(singletonList("ip"));
        given(ipAddressHelper.toIpAddress(anyString())).willReturn(IpAddress.of("ip", IpAddress.IP.v4));

        final TcfContext tcfContext = TcfContext.builder()
                .gdpr("1")
                .consentString("consent")
                .consent(TCStringEmpty.create())
                .build();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final String accountId = "account";

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromSetuidRequest(
                httpRequest, Account.empty(accountId), null);

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.EMPTY, 0);
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext));

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(MetricName.setuid, null, accountId);
        verify(tcfDefinerService).resolveTcfContext(
                eq(privacy), eq("ip"), isNull(), eq(MetricName.setuid), eq(expectedRequestLogInfo), isNull());
    }

    @Test
    public void contextFromCookieSyncRequestShouldReturnContext() {
        // given
        final HttpServerRequest httpRequest = mock(HttpServerRequest.class);
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        given(httpRequest.headers()).willReturn(headers);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        given(implicitParametersExtractor.ipFrom(eq(headers), eq("host"))).willReturn(singletonList("ip"));
        given(ipAddressHelper.toIpAddress(anyString())).willReturn(IpAddress.of("ip", IpAddress.IP.v4));

        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .gdpr(1)
                .gdprConsent("consent")
                .usPrivacy("1YYY")
                .build();

        final TcfContext tcfContext = TcfContext.builder()
                .gdpr("1")
                .consentString("consent")
                .consent(TCStringEmpty.create())
                .build();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final String accountId = "account";

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromCookieSyncRequest(
                cookieSyncRequest, httpRequest, Account.empty(accountId), null);

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext));

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(MetricName.cookiesync, null, accountId);
        verify(tcfDefinerService).resolveTcfContext(
                eq(privacy), eq("ip"), isNull(), eq(MetricName.cookiesync), eq(expectedRequestLogInfo), isNull());
    }

    @Test
    public void shouldCallMaskCoppaWhenDeviceLmtIsEnforceAndOneAndRegsCoppaIsOneAndDoesNotCallTcfServices() {
        // given
        final User user = notMaskedUser();

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1))));
        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 1);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(context, singletonMap(BIDDER_NAME, user), singletonList(BIDDER_NAME), aliases);

        // then
        verify(privacyAnonymizationService).maskCoppa(any(), any(), anyString());
        verifyNoInteractions(tcfDefinerService);
    }

    @Test
    public void shouldCallMaskCcpaWhenUsPolicyIsValidAndCoppaIsZero() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, true, false);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, emptyMap(), null)));

        given(bidderCatalog.bidderInfoByName(BIDDER_NAME)).willReturn(givenBidderInfo(1, true));

        final User user = notMaskedUser();

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(notMaskedDevice()));

        final AuctionContext context = auctionContext(
                bidRequest, givenPrivacyContext("1", Ccpa.of("1YYY"), 0));

        // when
        privacyEnforcementService.mask(context, singletonMap(BIDDER_NAME, user), singletonList(BIDDER_NAME), aliases);

        // then
        verify(privacyAnonymizationService).maskCcpa(any(), any(), anyString());
    }

    @Test
    public void shouldCallMaskCcpaWhenAccountHasCppaConfigEnabledForRequestType() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, false, true);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, emptyMap(), null)));

        given(bidderCatalog.bidderInfoByName(BIDDER_NAME)).willReturn(givenBidderInfo(1, true));

        final User user = notMaskedUser(notMaskedExtUser());

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(notMaskedDevice()));

        final AccountPrivacyConfig accountPrivacyConfig =
                AccountPrivacyConfig.of(
                        null,
                        AccountCcpaConfig.builder()
                                .enabledForRequestType(EnabledForRequestType.of(false, false, true, false))
                                .build());

        final AuctionContext context = AuctionContext.builder()
                .account(Account.builder().privacy(accountPrivacyConfig).build())
                .requestTypeMetric(MetricName.openrtb2app)
                .bidRequest(bidRequest)
                .timeout(timeout)
                .privacyContext(givenPrivacyContext("1", Ccpa.of("1YYY"), 0))
                .build();

        // when
        privacyEnforcementService.mask(context, singletonMap(BIDDER_NAME, user), singletonList(BIDDER_NAME), aliases);

        // then
        verify(privacyAnonymizationService).maskCcpa(any(), any(), any());
    }

    @Test
    public void shouldCallMaskCcpaWhenAccountHasCppaEnforcedTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, false, true);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, emptyMap(), null)));

        given(bidderCatalog.bidderInfoByName(BIDDER_NAME)).willReturn(givenBidderInfo(1, true));

        final User user = notMaskedUser(notMaskedExtUser());

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(notMaskedDevice()));

        final AccountPrivacyConfig accountPrivacyConfig = AccountPrivacyConfig.of(
                null, AccountCcpaConfig.builder().enabled(true).build());

        final AuctionContext context = AuctionContext.builder()
                .account(Account.builder().privacy(accountPrivacyConfig).build())
                .requestTypeMetric(MetricName.openrtb2app)
                .bidRequest(bidRequest)
                .timeout(timeout)
                .privacyContext(givenPrivacyContext("1", Ccpa.of("1YYY"), 0))
                .build();

        // when
        privacyEnforcementService.mask(context, singletonMap(BIDDER_NAME, user), singletonList(BIDDER_NAME), aliases);

        // then
        verify(privacyAnonymizationService).maskCcpa(any(), any(), any());
    }

    @Test
    public void shouldCallMaskCcpaWhenAccountHasCcpaConfigEnabled() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, false, true);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, emptyMap(), null)));

        given(bidderCatalog.bidderInfoByName(BIDDER_NAME)).willReturn(givenBidderInfo(1, true));

        final User user = notMaskedUser(notMaskedExtUser());

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(notMaskedDevice()));

        final AccountPrivacyConfig accountPrivacyConfig = AccountPrivacyConfig.of(
                null, AccountCcpaConfig.builder().enabled(true).build());

        final AuctionContext context = AuctionContext.builder()
                .account(Account.builder().privacy(accountPrivacyConfig).build())
                .requestTypeMetric(null)
                .bidRequest(bidRequest)
                .timeout(timeout)
                .privacyContext(givenPrivacyContext("1", Ccpa.of("1YYY"), 0))
                .build();

        // when
        privacyEnforcementService.mask(context, singletonMap(BIDDER_NAME, user), singletonList(BIDDER_NAME), aliases);

        // then
        verify(privacyAnonymizationService).maskCcpa(any(), any(), any());
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsFalseInConfigurationAndNullInAccount() {
        // given
        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsTrueInConfigurationAndFalseInAccount() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, true, false);

        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(null, AccountCcpaConfig.builder().enabled(false).build()))
                .build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, true, false);

        final Ccpa ccpa = Ccpa.of("1YNY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnTrueWhenEnforcedPropertyIsTrueAndCcpaReturnsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, true, false);

        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isTrue();
    }

    @Test
    public void shouldTolerateEmptyBidderToBidderPrivacyResultList() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList(),
                bidRequestBuilder -> bidRequestBuilder
                        .user(null)
                        .device(null));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, emptyMap(), singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
        verifyNoMoreInteractions(tcfDefinerService);

        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void shouldResolveBidderNameAndVendorIdsByAliases() {
        // given
        final String requestBidder1Name = "bidder1";
        final String requestBidder1Alias = "bidder1Alias";
        final String bidder2Name = "bidder2NotInRequest";
        final String bidder2Alias = "bidder2Alias";
        final Integer bidder2AliasVendorId = 220;
        final String requestBidder3Name = "bidder3";

        final User user = notMaskedUser();

        final Map<String, Integer> bidderToId = Map.of(
                requestBidder1Name, 1,
                requestBidder1Alias, 2,
                bidder2Alias, 3,
                requestBidder3Name, 4);

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(bidderToId),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(notMaskedDevice()));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        final Map<String, User> bidderToUser = Map.of(
                requestBidder1Name, notMaskedUser(),
                requestBidder1Alias, notMaskedUser(),
                bidder2Alias, notMaskedUser(),
                requestBidder3Name, notMaskedUser());

        final Map<String, PrivacyEnforcementAction> bidderNameToTcfEnforcement = Map.of(
                requestBidder1Name, PrivacyEnforcementAction.restrictAll(),
                requestBidder1Alias, PrivacyEnforcementAction.restrictAll(),
                bidder2Alias, restrictDeviceAndUser(),
                requestBidder3Name, PrivacyEnforcementAction.allowAll());

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, bidderNameToTcfEnforcement, null)));

        given(aliases.resolveBidder(eq(requestBidder1Alias))).willReturn(requestBidder1Name);
        given(aliases.resolveBidder(eq(bidder2Alias))).willReturn(bidder2Name);
        given(aliases.resolveAliasVendorId(eq(bidder2Alias))).willReturn(bidder2AliasVendorId);

        // when
        final List<String> bidders = List.of(requestBidder1Name, requestBidder1Alias, bidder2Alias, requestBidder3Name);
        privacyEnforcementService.mask(context, bidderToUser, bidders, aliases);

        // then
        final Set<String> bidderNames = Set.of(
                requestBidder1Name, requestBidder1Alias, bidder2Alias, requestBidder3Name);
        verify(tcfDefinerService).resultForBidderNames(eq(bidderNames), any(), any(), any());
    }

    @Test
    public void shouldCallMaskTcf() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(
                        Future.succeededFuture(
                                TcfResponse.of(
                                        true,
                                        singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()),
                                        null)));

        final User user = notMaskedUser();

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(givenNotMaskedDevice(identity())));

        final AuctionContext context = auctionContext(bidRequest, givenPrivacyContext("0", Ccpa.EMPTY, 0));

        // when
        privacyEnforcementService.mask(context, singletonMap(BIDDER_NAME, user), singletonList(BIDDER_NAME), aliases);

        // then
        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
        verify(privacyAnonymizationService).maskTcf(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendAnalyticsForEnforcedAndEnabledLmt() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService, tcfDefinerService,
                implicitParametersExtractor, ipAddressHelper, metrics, countryCodeMapper, false, true);

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().lmt(1).build()));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);
        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(context, emptyMap(), singletonList(BIDDER_NAME), aliases);

        // then
        verify(metrics).updatePrivacyLmtMetric();
    }

    private AuctionContext auctionContext(BidRequest bidRequest, PrivacyContext privacyContext) {
        return AuctionContext.builder()
                .account(Account.builder().build())
                .requestTypeMetric(MetricName.openrtb2web)
                .bidRequest(bidRequest)
                .timeout(timeout)
                .privacyContext(privacyContext)
                .build();
    }

    private static Device notMaskedDevice() {
        return Device.builder()
                .ip("192.168.0.10")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .geo(Geo.builder().lon(-85.34321F).lat(189.342323F).country("US").build())
                .ifa("ifa")
                .macsha1("macsha1")
                .macmd5("macmd5")
                .didsha1("didsha1")
                .didmd5("didmd5")
                .dpidsha1("dpidsha1")
                .dpidmd5("dpidmd5")
                .build();
    }

    private static User notMaskedUser() {
        return User.builder()
                .id("id")
                .buyeruid(BUYER_UID)
                .geo(Geo.builder().lon(-85.1245F).lat(189.9531F).country("US").build())
                .ext(ExtUser.builder().consent("consent").build())
                .build();
    }

    private static User notMaskedUser(ExtUser extUser) {
        return User.builder()
                .id("id")
                .buyeruid(BUYER_UID)
                .geo(Geo.builder().lon(-85.1245F).lat(189.9531F).country("US").build())
                .ext(extUser)
                .build();
    }

    private static ExtUser notMaskedExtUser() {
        return ExtUser.builder()
                .digitrust(mapper.createObjectNode())
                .eids(singletonList(ExtUserEid.of("Test", "id", emptyList(), null)))
                .prebid(ExtUserPrebid.of(singletonMap("key", "value")))
                .build();
    }

    private static ExtUser extUserIdsMasked() {
        return ExtUser.builder()
                .prebid(ExtUserPrebid.of(singletonMap("key", "value")))
                .build();
    }

    private static Device deviceCoppaMasked() {
        return Device.builder()
                .ip("192.168.0.0")
                .ipv6("2001:0db8:85a3:0000::")
                .geo(Geo.builder().country("US").build())
                .build();
    }

    private static User userCoppaMasked(ExtUser extUser) {
        return User.builder()
                .geo(Geo.builder().country("US").build())
                .ext(extUser)
                .build();
    }

    private static Device deviceTcfMasked() {
        return Device.builder()
                .ip("192.168.0.0")
                .ipv6("2001:0db8:85a3:0000::")
                .geo(Geo.builder().lon(-85.34F).lat(189.34F).country("US").build())
                .build();
    }

    private static User userTcfMasked() {
        return User.builder()
                .buyeruid(null)
                .geo(Geo.builder().lon(-85.12F).lat(189.95F).country("US").build())
                .ext(ExtUser.builder().consent("consent").build())
                .build();
    }

    private static User userTcfMasked(ExtUser extUser) {
        return User.builder()
                .buyeruid(null)
                .geo(Geo.builder().lon(-85.12F).lat(189.95F).country("US").build())
                .ext(extUser)
                .build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp,
                                              UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
    }

    private static Device givenNotMaskedDevice(UnaryOperator<Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(notMaskedDevice().toBuilder()).build();
    }

    private static User givenNotMaskedUser(UnaryOperator<User.UserBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(notMaskedUser().toBuilder()).build();
    }

    private static Device givenTcfMaskedDevice(UnaryOperator<Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(deviceTcfMasked().toBuilder()).build();
    }

    private static Device givenCoppaMaskedDevice(UnaryOperator<Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(deviceCoppaMasked().toBuilder()).build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
    }

    private static <T> Imp givenImp(T ext, UnaryOperator<Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder().ext(mapper.valueToTree(ext))).build();
    }

    private PrivacyContext givenPrivacyContext(String gdpr, Ccpa ccpa, Integer coppa) {
        return PrivacyContext.of(
                Privacy.of(gdpr, EMPTY, ccpa, coppa),
                TcfContext.empty());
    }

    private static PrivacyEnforcementAction restrictDeviceAndUser() {
        return PrivacyEnforcementAction.builder()
                .maskDeviceInfo(true)
                .maskDeviceIp(true)
                .maskGeo(true)
                .removeUserIds(true)
                .build();
    }

    private static PrivacyDebugLog privacyDebugLog() {
        return PrivacyDebugLog.from(
                Privacy.of("", "", Ccpa.EMPTY, 1),
                Privacy.of("", "", Ccpa.EMPTY, 1),
                TcfContext.empty());
    }

    private static BidderInfo givenBidderInfo(int gdprVendorId, boolean enforceCcpa) {
        return BidderInfo.of(
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                new BidderInfo.GdprInfo(gdprVendorId),
                enforceCcpa,
                false);
    }
}
