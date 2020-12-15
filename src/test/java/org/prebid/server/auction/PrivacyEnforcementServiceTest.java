package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
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
import org.prebid.server.auction.model.PreBidRequestContext;
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
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.request.PreBidRequest;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);

        privacyExtractor = new PrivacyExtractor();

        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, false, false);
    }

    @Test
    public void contextFromBidRequestShouldReturnCoppaContext() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .regs(Regs.of(1, null))
                .build();

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromBidRequest(
                bidRequest, Account.empty("account"), null, null, new ArrayList<>());

        // then
        FutureAssertion.assertThat(privacyContext).succeededWith(
                PrivacyContext.of(Privacy.of(EMPTY, EMPTY, Ccpa.EMPTY, 1), TcfContext.empty()));
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
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext));

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
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext, "ip-masked"));

        verify(tcfDefinerService).resolveTcfContext(
                eq(privacy), isNull(), eq("ip-masked"), isNull(), same(MetricName.openrtb2web), any(), isNull());
    }

    @Test
    public void contextFromLegacyRequestShouldReturnContext() {
        // given
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder()
                .preBidRequest(PreBidRequest.builder()
                        .regs(Regs.of(null, ExtRegs.of(1, "1YYY")))
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent("consent")
                                        .build())
                                .build())
                        .build())
                .ip("ip")
                .build();

        final TcfContext tcfContext = TcfContext.builder()
                .gdpr("1")
                .consentString("consent")
                .consent(TCStringEmpty.create())
                .build();
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final String accountId = "account";

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromLegacyRequest(
                preBidRequestContext, Account.empty(accountId));

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext));

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(MetricName.legacy, null, accountId);
        verify(tcfDefinerService)
                .resolveTcfContext(eq(privacy), eq("ip"), isNull(), eq(expectedRequestLogInfo), isNull());
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
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final String accountId = "account";

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromSetuidRequest(
                request, Account.empty(accountId), null);

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.EMPTY, 0);
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext));

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(MetricName.setuid, null, accountId);
        verify(tcfDefinerService)
                .resolveTcfContext(eq(privacy), eq("ip"), isNull(), eq(expectedRequestLogInfo), isNull());
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
        given(tcfDefinerService.resolveTcfContext(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(tcfContext));

        final String accountId = "account";

        // when
        final Future<PrivacyContext> privacyContext = privacyEnforcementService.contextFromCookieSyncRequest(
                cookieSyncRequest, httpServerRequest, Account.empty(accountId), null);

        // then
        final Privacy privacy = Privacy.of("1", "consent", Ccpa.of("1YYY"), 0);
        FutureAssertion.assertThat(privacyContext).succeededWith(PrivacyContext.of(privacy, tcfContext));

        final RequestLogInfo expectedRequestLogInfo = RequestLogInfo.of(MetricName.cookiesync, null, accountId);
        verify(tcfDefinerService)
                .resolveTcfContext(eq(privacy), eq("ip"), isNull(), eq(expectedRequestLogInfo), isNull());
    }

    @Test
    public void shouldMaskForCoppaWhenDeviceLmtIsEnforceAndOneAndRegsCoppaIsOneAndDoesNotCallTcfServices() {
        // given
        final User user = notMaskedUser(notMaskedExtUser());
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
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expected = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(userCoppaMasked(extUserIdsMasked()))
                .device(givenCoppaMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1)))
                .build();
        assertThat(result).isEqualTo(singletonList(expected));

        verifyZeroInteractions(tcfDefinerService);
    }

    @Test
    public void shouldMaskForCcpaWhenUsPolicyIsValidAndCoppaIsZero() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, true, false);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, emptyMap(), null)));

        given(bidderCatalog.bidderInfoByName(BIDDER_NAME)).willReturn(givenBidderInfo(1, true));

        final User user = notMaskedUser(notMaskedExtUser());
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
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expected = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(userTcfMasked(extUserIdsMasked()))
                .device(deviceTcfMasked())
                .build();
        assertThat(result).isEqualTo(singletonList(expected));
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
    public void shouldNotMaskWhenDeviceLmtIsNullAndGdprNotEnforced() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(notMaskedUser())
                .device(notMaskedDevice())
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsZeroAndCoppaIsZeroAndGdprIsZeroAndTcfDefinerServiceAllowAll() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(0));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(notMaskedUser())
                .device(givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(0)))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldMaskForTcfWhenTcfServiceAllowAllAndDeviceLmtIsOneAndLmtIsEnforced() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, false, true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

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
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(userTcfMasked())
                .device(givenTcfMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1)))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldNotMaskForTcfWhenTcfServiceAllowAllAndDeviceLmtIsOneAndLmtIsNotEnforced() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Regs regs = Regs.of(0, null);
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), BidderAliases.of(null, null, bidderCatalog))
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(user)
                .device(device)
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldMaskForTcfWhenTcfDefinerServiceRestrictDeviceAndUser() {
        // given
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(userTcfMasked())
                .device(deviceTcfMasked())
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldMaskUserIdsWhenTcfDefinerServiceRestrictUserIds() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setRemoveUserIds(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final User user = notMaskedUser(notMaskedExtUser());
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(givenNotMaskedUser(userBuilder -> userBuilder
                        .id(null)
                        .buyeruid(null)
                        .ext(extUserIdsMasked())))
                .device(notMaskedDevice())
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());

        verify(metrics).updateAuctionTcfMetrics(eq(BIDDER_NAME), any(), eq(true), anyBoolean(), anyBoolean(),
                anyBoolean());
    }

    @Test
    public void shouldMaskUserIdsWhenTcfDefinerServiceRestrictUserIdsAndReturnNullWhenAllValuesMasked() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setRemoveUserIds(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = ExtUser.builder()
                .eids(singletonList(ExtUserEid.of("Test", "id", emptyList(), null)))
                .build();
        final User user = User.builder()
                .buyeruid(BUYER_UID)
                .ext(extUser)
                .build();

        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldMaskGeoWhenTcfDefinerServiceRestrictGeo() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskGeo(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(givenNotMaskedUser(userBuilder -> userBuilder.geo(userTcfMasked().getGeo())))
                .device(givenNotMaskedDevice(deviceBuilder -> deviceBuilder.geo(deviceTcfMasked().getGeo())))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());

        verify(metrics).updateAuctionTcfMetrics(eq(BIDDER_NAME), any(), anyBoolean(), eq(true), anyBoolean(),
                anyBoolean());
    }

    @Test
    public void shouldMaskDeviceIpWhenTcfDefinerServiceRestrictDeviceIp() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskDeviceIp(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final Device deviceTcfMasked = deviceTcfMasked();
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(notMaskedUser())
                .device(givenNotMaskedDevice(
                        deviceBuilder -> deviceBuilder.ip(deviceTcfMasked.getIp()).ipv6(deviceTcfMasked.getIpv6())))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldMaskDeviceInfoWhenTcfDefinerServiceRestrictDeviceInfo() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskDeviceInfo(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final Device deviceInfoMasked = givenNotMaskedDevice(deviceBuilder -> deviceBuilder
                .ifa(null)
                .macsha1(null).macmd5(null)
                .dpidsha1(null).dpidmd5(null)
                .didsha1(null).didmd5(null));
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(notMaskedUser())
                .device(deviceInfoMasked)
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
    }

    @Test
    public void shouldSendAnalyticsBlockedMetricIfRestrictedByPrivacyEnforcement() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setBlockAnalyticsReport(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap(BIDDER_NAME, 1)), identity());
        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);
        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(context, emptyMap(), singletonList(BIDDER_NAME), aliases);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq(BIDDER_NAME), any(), anyBoolean(), anyBoolean(), eq(true),
                anyBoolean());
    }

    @Test
    public void shouldNotSendRelatedMetricsIfBlockBidderRequestEnforcementIsPresent() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setBlockBidderRequest(true); // has highest priority
        privacyEnforcementAction.setRemoveUserIds(true);
        privacyEnforcementAction.setMaskGeo(true);
        privacyEnforcementAction.setBlockAnalyticsReport(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = notMaskedExtUser();
        final User user = notMaskedUser(extUser);
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(notMaskedDevice()));
        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq(BIDDER_NAME), any(), eq(false), eq(false), eq(false),
                eq(true));
    }

    @Test
    public void shouldNotSendUserIdRemovedMetricIfNoPrivateUserInformation() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setRemoveUserIds(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = ExtUser.builder().consent("consent").build();
        final User user = User.builder().gender("gender").ext(extUser).build();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user));
        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq(BIDDER_NAME), any(), eq(false), anyBoolean(), anyBoolean(),
                anyBoolean());
    }

    @Test
    public void shouldNotSendGeoMaskedMetricIfNoPrivateGeoInformation() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskGeo(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder().model("blackberry").build()));
        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        privacyEnforcementService.mask(context, emptyMap(), singletonList(BIDDER_NAME), aliases);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq(BIDDER_NAME), any(), eq(false), anyBoolean(), anyBoolean(),
                anyBoolean());
    }

    @Test
    public void shouldRerunEmptyResultWhenTcfDefinerServiceRestrictRequest() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.restrictAll()), null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .blockedRequestByTcf(true)
                .blockedAnalyticsByTcf(true)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService).resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), any(), any());
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
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, bidders, aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidder1Masked = BidderPrivacyResult.builder()
                .requestBidder(requestBidder1Name)
                .blockedAnalyticsByTcf(true)
                .blockedRequestByTcf(true)
                .build();
        final BidderPrivacyResult expectedBidderAlias1Masked = BidderPrivacyResult.builder()
                .requestBidder(requestBidder1Alias)
                .blockedAnalyticsByTcf(true)
                .blockedRequestByTcf(true)
                .build();
        final BidderPrivacyResult expectedBidderAlias2Masked = BidderPrivacyResult.builder()
                .requestBidder(bidder2Alias)
                .user(userTcfMasked())
                .device(deviceTcfMasked())
                .build();
        final BidderPrivacyResult expectedBidder3Masked = BidderPrivacyResult.builder()
                .requestBidder(requestBidder3Name)
                .user(notMaskedUser())
                .device(notMaskedDevice())
                .build();
        assertThat(result).containsOnly(
                expectedBidder1Masked, expectedBidderAlias1Masked, expectedBidderAlias2Masked, expectedBidder3Masked);

        final Set<String> bidderNames = new HashSet<>(asList(
                requestBidder1Name, requestBidder1Alias, bidder2Alias, requestBidder3Name));
        verify(tcfDefinerService).resultForBidderNames(eq(bidderNames), any(), any(), any());
    }

    @Test
    public void shouldNotReturnUserIfMaskingAppliedAndUserBecameEmptyObject() {
        // given
        final ExtUser extUser = ExtUser.builder()
                .eids(singletonList(ExtUserEid.of("Test", "id", emptyList(), null)))
                .build();
        final User user = User.builder()
                .buyeruid("buyeruid")
                .ext(extUser)
                .build();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user));

        final PrivacyContext privacyContext = givenPrivacyContext("0", Ccpa.EMPTY, 1);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);
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
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, true, false);

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
        final List<BidderPrivacyResult> result = privacyEnforcementService.mask(
                context,
                bidderToUser,
                asList(bidder1Name, bidder2Name, bidder3Name),
                BidderAliases.of(bidderCatalog))
                .result();

        // then
        assertThat(result).containsOnly(
                BidderPrivacyResult.builder()
                        .requestBidder(bidder1Name)
                        .device(deviceTcfMasked())
                        .user(userTcfMasked())
                        .blockedRequestByTcf(false)
                        .blockedAnalyticsByTcf(false)
                        .build(),
                BidderPrivacyResult.builder()
                        .requestBidder(bidder2Name)
                        .blockedRequestByTcf(true)
                        .blockedAnalyticsByTcf(true)
                        .build(),
                BidderPrivacyResult.builder()
                        .requestBidder(bidder3Name)
                        .blockedRequestByTcf(true)
                        .blockedAnalyticsByTcf(true)
                        .build());
    }

    @Test
    public void shouldNotMaskForCcpaWhenCatchAllWildcardIsPresentInNosaleList() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, true, false);

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
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, singletonList(BIDDER_NAME), BidderAliases.of(bidderCatalog))
                .result();

        // then
        assertThat(result).containsOnly(
                BidderPrivacyResult.builder()
                        .requestBidder(BIDDER_NAME)
                        .user(notMaskedUser())
                        .device(notMaskedDevice())
                        .blockedRequestByTcf(false)
                        .blockedAnalyticsByTcf(false)
                        .build());
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
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, true, false);

        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder().enforceCcpa(false).build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, true, false);

        final Ccpa ccpa = Ccpa.of("1YNY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnTrueWhenEnforcedPropertyIsTrueAndCcpaReturnsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, privacyExtractor, tcfDefinerService, ipAddressHelper, metrics, true, false);

        final Ccpa ccpa = Ccpa.of("1YYY");
        final Account account = Account.builder().build();

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa, account)).isTrue();
    }

    @Test
    public void shouldReturnCorrectMaskedForMultipleBidders() {
        // given
        final String bidder1Name = "bidder1";
        final String bidder2Name = "bidder2";
        final String bidder3Name = "bidder3";

        final Map<String, PrivacyEnforcementAction> vendorIdToTcfEnforcement = new HashMap<>();
        vendorIdToTcfEnforcement.put(bidder1Name, PrivacyEnforcementAction.restrictAll());
        vendorIdToTcfEnforcement.put(bidder2Name, restrictDeviceAndUser());
        vendorIdToTcfEnforcement.put(bidder3Name, PrivacyEnforcementAction.allowAll());
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, vendorIdToTcfEnforcement, null)));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = new HashMap<>();
        bidderToUser.put(bidder1Name, notMaskedUser());
        bidderToUser.put(bidder2Name, notMaskedUser());
        bidderToUser.put(bidder3Name, notMaskedUser());
        final List<String> bidders = asList(bidder1Name, bidder2Name, bidder3Name);

        final HashMap<String, Integer> bidderToId = new HashMap<>();
        bidderToId.put(bidder1Name, 1);
        bidderToId.put(bidder2Name, 2);
        bidderToId.put(bidder3Name, 3);
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(bidderToId),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, bidders, aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidder1Masked = BidderPrivacyResult.builder()
                .requestBidder(bidder1Name)
                .blockedAnalyticsByTcf(true)
                .blockedRequestByTcf(true)
                .build();
        final BidderPrivacyResult expectedBidder2Masked = BidderPrivacyResult.builder()
                .requestBidder(bidder2Name)
                .user(userTcfMasked())
                .device(deviceTcfMasked())
                .build();
        final BidderPrivacyResult expectedBidder3Masked = BidderPrivacyResult.builder()
                .requestBidder(bidder3Name)
                .user(notMaskedUser())
                .device(notMaskedDevice())
                .build();
        assertThat(result).hasSize(3)
                .containsOnly(expectedBidder1Masked, expectedBidder2Masked, expectedBidder3Masked);

        final HashSet<String> bidderNames = new HashSet<>(asList(bidder1Name, bidder2Name, bidder3Name));
        verify(tcfDefinerService).resultForBidderNames(eq(bidderNames), any(), any(), any());
    }

    @Test
    public void shouldIncrementCcpaAndAuctionTcfMetrics() {
        // given
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap("someAlias", user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap("someAlias", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final PrivacyContext privacyContext = givenPrivacyContext("1", Ccpa.EMPTY, 0);

        final AuctionContext context = auctionContext(bidRequest, privacyContext);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap("someAlias", restrictDeviceAndUser()), null)));

        given(aliases.resolveBidder(eq("someAlias"))).willReturn(BIDDER_NAME);

        // when
        privacyEnforcementService.mask(context, bidderToUser, singletonList("someAlias"), aliases);

        // then
        verify(metrics).updatePrivacyCcpaMetrics(eq(false), eq(false));
        verify(metrics).updateAuctionTcfMetrics(
                eq(BIDDER_NAME), eq(MetricName.openrtb2web), eq(true), eq(true), eq(false), eq(false));
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

    private static BidderInfo givenBidderInfo(int gdprVendorId, boolean enforceCcpa) {
        return new BidderInfo(true, null, null, null,
                new BidderInfo.GdprInfo(gdprVendorId, true), enforceCcpa, false);
    }
}
