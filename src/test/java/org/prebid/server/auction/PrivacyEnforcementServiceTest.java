package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.assertion.FutureAssertion;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

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
    private PrivacyAnonymizationService privacyAnonymizationService;
    @Mock
    private IpAddressHelper ipAddressHelper;
    @Mock
    private Metrics metrics;

    private PrivacyEnforcementService privacyEnforcementService;

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

        given(privacyAnonymizationService.maskCoppa(any(), any(), any()))
                .willReturn(givenBidderPrivacyResult());
        given(privacyAnonymizationService.maskCcpa(any(), any(), any()))
                .willReturn(givenBidderPrivacyResult());
        given(privacyAnonymizationService.maskTcf(any(), any(), any(), any(), any(), any()))
                .willReturn(givenBidderPrivacyResult());

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);

        privacyExtractor = new PrivacyExtractor();

        privacyEnforcementService = new PrivacyEnforcementService(bidderCatalog, privacyExtractor,
                privacyAnonymizationService, tcfDefinerService, ipAddressHelper, metrics, false, false);
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
                .build();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        // when
        final Future<PrivacyContext> privacyContextResult = privacyEnforcementService.contextFromBidRequest(
                bidRequest, Account.empty("account"), null, null, new ArrayList<>());

        // then
        FutureAssertion.assertThat(privacyContextResult)
                .succeededSatisfies(privacyContext -> {
                    assertThat(privacyContext).extracting(PrivacyContext::getPrivacy)
                            .containsExactly(Privacy.of(EMPTY, EMPTY, Ccpa.EMPTY, 1));
                    assertThat(privacyContext).extracting(PrivacyContext::getTcfContext).containsExactly(tcfContext);
                });
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
                .build();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final String accountId = "account";
        final MetricName requestType = MetricName.openrtb2web;

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromBidRequest(
                bidRequest, Account.empty(accountId), requestType, null, new ArrayList<>());

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        assertThat(privacyContext.succeeded()).isTrue();
        assertThat(privacyContext.result())
                .extracting(PrivacyContext::getPrivacy, PrivacyContext::getTcfContext)
                .containsOnly(privacy, tcfContext);

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(requestType, referer, accountId);
        verify(tcfDefinerService).resolveTcfContext(
                eq(privacy), isNull(), isNull(), isNull(), same(requestType), eq(expectedRequestLogInfo), isNull());
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
                .ipAddress("ip-masked")
                .inEea(false)
                .geoInfo(GeoInfo.builder().vendor("v").country("ua").build())
                .build();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromBidRequest(
                bidRequest, Account.empty("account"), MetricName.openrtb2web, null, new ArrayList<>());

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        assertThat(privacyContext.succeeded()).isTrue();
        assertThat(privacyContext.result())
                .extracting(PrivacyContext::getTcfContext, PrivacyContext::getIpAddress)
                .containsOnly(tcfContext, "ip-masked");
        verify(tcfDefinerService).resolveTcfContext(
                eq(privacy), isNull(), eq("ip-masked"), isNull(), same(MetricName.openrtb2web), any(), isNull());
    }

    @Test
    public void contextFromSetuidRequestShouldReturnContext() {
        // given
        final HttpServerRequest request = mock(HttpServerRequest.class);
        given(request.getParam("gdpr")).willReturn("1");
        given(request.getParam("gdpr_consent")).willReturn("consent");
        given(request.headers()).willReturn(MultiMap.caseInsensitiveMultiMap().add("X-Forwarded-For", "ip"));

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
                request, Account.empty(accountId), null);

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
        final HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        given(httpServerRequest.headers())
                .willReturn(MultiMap.caseInsensitiveMultiMap().add("X-Forwarded-For", "ip"));

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
                cookieSyncRequest, httpServerRequest, Account.empty(accountId), null);

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
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));
        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 1);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        verify(privacyAnonymizationService).maskCoppa(any(), any(), anyString());
        verifyZeroInteractions(tcfDefinerService);
    }

    @Test
    public void shouldCallMaskForCcpaWhenUsPolicyIsValidAndCoppaIsZero() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService,
                tcfDefinerService, ipAddressHelper, metrics, true, false);
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, emptyMap(), null)));

        given(bidderCatalog.bidderInfoByName(BIDDER_NAME)).willReturn(givenBidderInfo(1, true));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.of("1YYY"), 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        verify(privacyAnonymizationService).maskCcpa(any(), any(), anyString());
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
        final Device device = notMaskedDevice();
        final HashMap<String, Integer> bidderToId = new HashMap<>();
        bidderToId.put(requestBidder1Name, 1);
        bidderToId.put(requestBidder1Alias, 2);
        bidderToId.put(bidder2Alias, 3);
        bidderToId.put(requestBidder3Name, 4);
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(bidderToId),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        final Map<String, User> bidderToUser = new HashMap<>();
        bidderToUser.put(requestBidder1Name, notMaskedUser());
        bidderToUser.put(requestBidder1Alias, notMaskedUser());
        bidderToUser.put(bidder2Alias, notMaskedUser());
        bidderToUser.put(requestBidder3Name, notMaskedUser());

        final Map<String, PrivacyEnforcementAction> bidderNameToTcfEnforcement = new HashMap<>();
        bidderNameToTcfEnforcement.put(requestBidder1Name, PrivacyEnforcementAction.restrictAll());
        bidderNameToTcfEnforcement.put(requestBidder1Alias, PrivacyEnforcementAction.restrictAll());
        bidderNameToTcfEnforcement.put(bidder2Alias, restrictDeviceAndUser());
        bidderNameToTcfEnforcement.put(requestBidder3Name, PrivacyEnforcementAction.allowAll());

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, bidderNameToTcfEnforcement, null)));

        given(aliases.resolveBidder(eq(requestBidder1Alias))).willReturn(requestBidder1Name);
        given(aliases.resolveBidder(eq(bidder2Alias))).willReturn(bidder2Name);
        given(aliases.resolveAliasVendorId(eq(bidder2Alias))).willReturn(bidder2AliasVendorId);

        // when
        final List<String> bidders =
                asList(requestBidder1Name, requestBidder1Alias, bidder2Alias, requestBidder3Name);
        privacyEnforcementService.mask(context, bidderToUser, bidders, aliases).result();

        // then
        final Set<String> bidderNames = new HashSet<>(asList(
                requestBidder1Name, requestBidder1Alias, bidder2Alias, requestBidder3Name));
        verify(tcfDefinerService).resultForBidderNames(eq(bidderNames), any(), any(), any());
    }

    @Test
    public void shouldReturnFailedFutureWhenTcfServiceIsReturnFailedFuture() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException(
                        "Error when retrieving allowed purpose ids in a reason of invalid consent string")));

        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final Future<List<BidderPrivacyResult>> firstFuture = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases);

        // then
        assertThat(firstFuture.failed()).isTrue();
        assertThat(firstFuture.cause().getMessage())
                .isEqualTo("Error when retrieving allowed purpose ids in a reason of invalid consent string");

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
        verifyNoMoreInteractions(tcfDefinerService);
    }

    @Test
    public void shouldMaskForCcpaAndTcfWhenUsPolicyIsValidAndGdprIsEnforcedAndCOPPAIsZero() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService,
                tcfDefinerService, ipAddressHelper, metrics, true, false);

        final String bidder1Name = "bidder1Name";
        final String bidder2Name = "bidder2Name";
        final String bidder3Name = "bidder3Name";

        given(bidderCatalog.bidderInfoByName(bidder1Name)).willReturn(givenBidderInfo(1, true));
        given(bidderCatalog.bidderInfoByName(bidder2Name)).willReturn(givenBidderInfo(2, true));
        given(bidderCatalog.bidderInfoByName(bidder3Name)).willReturn(givenBidderInfo(3, false));

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = new HashMap<>();
        bidderNameToAction.put(bidder2Name, PrivacyEnforcementAction.restrictAll());
        bidderNameToAction.put(bidder3Name, PrivacyEnforcementAction.restrictAll());
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, bidderNameToAction, null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();

        final Map<String, User> bidderToUser = new HashMap<>();
        bidderToUser.put(bidder1Name, notMaskedUser());
        bidderToUser.put(bidder2Name, notMaskedUser());
        bidderToUser.put(bidder3Name, notMaskedUser());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .nosale(singletonList(bidder2Name))
                                .build())));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.of("1YYY"), 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(
                context,
                bidderToUser,
                asList(bidder1Name, bidder2Name, bidder3Name),
                BidderAliases.of(bidderCatalog))
                .result();

        // then
        verify(privacyAnonymizationService).maskCcpa(any(), any(), eq(bidder1Name));
        verify(privacyAnonymizationService).maskTcf(any(), any(), eq(bidder2Name), any(), any(), any());
        verify(privacyAnonymizationService).maskTcf(any(), any(), eq(bidder3Name), any(), any(), any());
    }

    @Test
    public void shouldNotMaskForCcpaWhenCatchAllWildcardIsPresentInNosaleList() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService,
                tcfDefinerService, ipAddressHelper, metrics, true, false);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();

        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, notMaskedUser());

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .nosale(singletonList("*"))
                                .build())));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.of("1YYY"), 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), BidderAliases.of(bidderCatalog))
                .result();

        // then
        verify(privacyAnonymizationService, never()).maskCcpa(any(), any(), eq(BIDDER_NAME));
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
                bidderCatalog, privacyExtractor, privacyAnonymizationService,
                tcfDefinerService, ipAddressHelper, metrics, true, false);

        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder().enforceCcpa(false).build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService,
                tcfDefinerService, ipAddressHelper, metrics, true, false);

        final Ccpa ccpa = Ccpa.of("1YNY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnTrueWhenEnforcedPropertyIsTrueAndCcpaReturnsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, privacyAnonymizationService,
                tcfDefinerService, ipAddressHelper, metrics, true, false);

        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isTrue();
    }

    @Test
    public void shouldIncrementCcpaMetrics() {
        // given
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap("someAlias", user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap("someAlias", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap("someAlias", restrictDeviceAndUser()), null)));

        given(aliases.resolveBidder(eq("someAlias"))).willReturn(BIDDER_NAME);

        // when
        privacyEnforcementService.mask(context, bidderToUser, singletonList("someAlias"), aliases);

        // then
        verify(metrics).updatePrivacyCcpaMetrics(eq(false), eq(false));
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
                .build();
    }

    private static User notMaskedUser() {
        return User.builder()
                .id("id")
                .language("lan")
                .build();
    }

    private static Device deviceMasked() {
        return Device.builder()
                .ip("192.168.0.0")
                .build();
    }

    private static User userMasked() {
        return User.builder()
                .language("lan")
                .build();
    }

    private static Device givenNotMaskedDevice(UnaryOperator<Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(notMaskedDevice().toBuilder()).build();
    }

    private BidderPrivacyResult givenBidderPrivacyResult() {
        return BidderPrivacyResult.builder()
                .user(userMasked())
                .device(deviceMasked())
                .requestBidder(BIDDER_NAME)
                .build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp,
                                              UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
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

    private static BidderInfo givenBidderInfo(int gdprVendorId, boolean enforceCcpa) {
        return new BidderInfo(true, null, null, null,
                new BidderInfo.GdprInfo(gdprVendorId, true), enforceCcpa, false);
    }
}
