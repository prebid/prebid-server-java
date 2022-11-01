package org.prebid.server.cookie;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodChooser;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.exception.InvalidCookieSyncRequestException;
import org.prebid.server.cookie.exception.UnauthorizedUidsException;
import org.prebid.server.cookie.model.BiddersContext;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.cookie.model.RejectionReason;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class CookieSyncServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private HostVendorTcfDefinerService hostVendorTcfDefinerService;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private CoopSyncProvider coopSyncProvider;
    @Mock
    private Metrics metrics;
    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private UsersyncMethodChooser usersyncMethodChooser;

    private CookieSyncService target;

    @Before
    public void setUp() {
        given(uidsCookie.allowsSync()).willReturn(true);
        given(hostVendorTcfDefinerService.isAllowedForHostVendorId(any()))
                .willReturn(Future.succeededFuture(HostVendorTcfResponse.allowedVendor()));
        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(false, emptyMap(), "country")));

        givenCookieSyncService(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void processContextShouldReturnErrorWithTcfContextIfUidsCookieDoesntAllowSync() {
        // given
        given(uidsCookie.allowsSync()).willReturn(false);
        final TcfContext tcfContext = TcfContext.builder().consentString("consent-string").build();
        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .privacyContext(givenPrivacyContext(tcfContext))
                .uidsCookie(uidsCookie)
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isFailed()
                .isInstanceOf(UnauthorizedUidsException.class)
                .hasMessage("Sync is not allowed for this uids")
                .extracting(error -> ((UnauthorizedUidsException) error).tcfContext)
                .isEqualTo(tcfContext);
    }

    @Test
    public void processContextShouldReturnErrorWithTcfContextIfInGdprAndConsentIsBlank() {
        // given
        final TcfContext tcfContext = TcfContext.builder().consentString("consent-string").build();
        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().gdpr(1).build())
                .privacyContext(givenPrivacyContext(tcfContext))
                .uidsCookie(uidsCookie)
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isFailed()
                .isInstanceOf(InvalidCookieSyncRequestException.class)
                .hasMessage("gdpr_consent is required if gdpr is 1")
                .extracting(error -> ((InvalidCookieSyncRequestException) error).tcfContext)
                .isEqualTo(tcfContext);
    }

    @Test
    public void processContextShouldReturnErrorWithTcfContextIfInGdprAndConsentIsInvalid() {
        // given
        final TcfContext tcfContext = TcfContext.builder()
                .inGdprScope(true)
                .consentValid(false)
                .consentString("consent-string")
                .build();

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().gdpr(1).gdprConsent("consent-string").build())
                .privacyContext(givenPrivacyContext(tcfContext))
                .uidsCookie(uidsCookie)
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        verify(metrics).updateUserSyncTcfInvalidMetric();
        assertThat(result).isFailed()
                .isInstanceOf(InvalidCookieSyncRequestException.class)
                .hasMessage("Consent string is invalid")
                .extracting(error -> ((InvalidCookieSyncRequestException) error).tcfContext)
                .isEqualTo(tcfContext);
    }

    @Test
    public void processContextShouldResolveLimitFromRequestWhenPresent() {
        // given
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .limit(42)
                .build();

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(cookieSyncRequest)
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .account(givenEmptyAccount())
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getLimit)
                .isEqualTo(42);
    }

    @Test
    public void processContextShouldResolveLimitFromAccountWhenPresentAndAbsentInRequest() {
        // given
        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .account(givenAccount(42, Integer.MAX_VALUE))
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getLimit)
                .isEqualTo(42);
    }

    @Test
    public void processContextShouldResolveLimitWithDefaultValueWhenAbsentInRequestAndAccount() {
        // given
        givenCookieSyncService(42, Integer.MAX_VALUE);

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .account(givenEmptyAccount())
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getLimit)
                .isEqualTo(42);
    }

    @Test
    public void processContextShouldCapLimitWithMaxLimitFromAccountWhenPresent() {
        // given
        givenCookieSyncService(Integer.MAX_VALUE, Integer.MAX_VALUE);

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().limit(100).build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .account(givenAccount(Integer.MAX_VALUE, 42))
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getLimit)
                .isEqualTo(42);
    }

    @Test
    public void processContextShouldCapLimitWithDefaultMaxLimitWhenMaxLimitFromAccountIsAbsent() {
        // given
        givenCookieSyncService(Integer.MAX_VALUE, 42);

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().limit(100).build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .account(givenEmptyAccount())
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getLimit)
                .isEqualTo(42);
    }

    @Test
    public void processContextShouldResolveBiddersToSync() {
        // given
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(singleton("coop-sync-bidder"));

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().bidders(singleton("requested-bidder")).build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .account(givenEmptyAccount())
                .build();

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result)
                .isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .satisfies(context -> {
                    assertThat(context.requestedBidders()).containsExactly("requested-bidder");
                    assertThat(context.coopSyncBidders()).containsExactly("coop-sync-bidder");
                    assertThat(context.multiSyncBidders()).isEmpty(); // TODO: add multisync
                });
    }

    @Test
    public void processContextShouldRejectInvalidBidders() {
        // given
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(singleton("coop-sync-bidder"));

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any())).willReturn(
                Future.succeededFuture(givenAllAllowedTcfResponse("requested-bidder", "coop-sync-bidder")));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(builder ->
                builder.cookieSyncRequest(givenCookieSyncRequest("requested-bidder", "invalid-bidder")));

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .extracting(BiddersContext::rejectedBidders)
                .isEqualTo(Map.of("invalid-bidder", RejectionReason.INVALID_BIDDER));
    }

    @Test
    public void processContextShouldRejectDisabledBidders() {
        // given
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(singleton("coop-sync-bidder"));

        given(bidderCatalog.isValidName("disabled-bidder")).willReturn(true);
        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any())).willReturn(
                Future.succeededFuture(givenAllAllowedTcfResponse("requested-bidder", "coop-sync-bidder")));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(builder ->
                builder.cookieSyncRequest(givenCookieSyncRequest("requested-bidder", "disabled-bidder")));

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .extracting(BiddersContext::rejectedBidders)
                .isEqualTo(Map.of("disabled-bidder", RejectionReason.DISABLED_BIDDER));
    }

    @Test
    public void processContextShouldRejectBiddersWithoutUsersync() {
        // given
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(singleton("coop-sync-bidder"));

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder", "bidder-without-usersync");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any())).willReturn(
                Future.succeededFuture(givenAllAllowedTcfResponse("requested-bidder", "coop-sync-bidder")));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(builder ->
                builder.cookieSyncRequest(givenCookieSyncRequest("requested-bidder", "bidder-without-usersync")));

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .extracting(BiddersContext::rejectedBidders)
                .isEqualTo(Map.of("bidder-without-usersync", RejectionReason.UNCONFIGURED_USERSYNC));
    }

    @Test
    public void processContextShouldApplyRequestFilteringRules() {
        // given
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(singleton("coop-sync-bidder"));

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        given(usersyncMethodChooser.choose(any(), eq("coop-sync-bidder"))).willReturn(null);

        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any())).willReturn(
                Future.succeededFuture(givenAllAllowedTcfResponse("requested-bidder", "coop-sync-bidder")));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(builder ->
                builder.cookieSyncRequest(givenCookieSyncRequest("requested-bidder")));

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .extracting(BiddersContext::rejectedBidders)
                .isEqualTo(Map.of("coop-sync-bidder", RejectionReason.REJECTED_BY_FILTER));
    }

    @Test
    public void processContextShouldApplyPrivacyFilteringRules() {
        // given
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(singleton("coop-sync-bidder"));

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, PrivacyEnforcementAction> actions = Map.of(
                "requested-bidder", PrivacyEnforcementAction.restrictAll(),
                "coop-sync-bidder", PrivacyEnforcementAction.allowAll());
        final TcfResponse<String> tcfResponse = TcfResponse.of(true, actions, "country");

        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any()))
                .willReturn(Future.succeededFuture(tcfResponse));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(builder ->
                builder.cookieSyncRequest(givenCookieSyncRequest("requested-bidder")));

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .extracting(BiddersContext::rejectedBidders)
                .isEqualTo(Map.of("requested-bidder", RejectionReason.REJECTED_BY_TCF));
    }

    private PrivacyContext givenAllAllowedPrivacyContext() {
        return givenPrivacyContext(TcfContext.builder().inGdprScope(false).build());
    }

    private TcfResponse<String> givenAllAllowedTcfResponse(String... bidders) {
        final Map<String, PrivacyEnforcementAction> actions = Arrays.stream(bidders)
                .collect(Collectors.toMap(identity(), ignored -> PrivacyEnforcementAction.allowAll()));

        return TcfResponse.of(false, actions, "country");
    }

    private PrivacyContext givenPrivacyContext(TcfContext tcfContext) {
        return PrivacyContext.of(Privacy.of("gdpr", "consent-string", Ccpa.EMPTY, 1), tcfContext);
    }

    private void givenValidActiveBidders(String... bidders) {
        Arrays.stream(bidders).forEach(this::givenValidActiveBidder);
    }

    private void givenValidActiveBidder(String bidder) {
        given(bidderCatalog.isValidName(bidder)).willReturn(true);
        given(bidderCatalog.isActive(bidder)).willReturn(true);
    }

    private void givenUsersyncersForBidders(String... bidders) {
        Arrays.stream(bidders).forEach(this::givenUsersyncerForBidder);
    }

    private void givenUsersyncerForBidder(String bidder) {
        givenUsersyncerForBidder(bidder, bidder);
    }

    private void givenUsersyncerForBidder(String bidder, String cookieFamilyName) {
        final UsersyncMethod usersyncMethod = UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl("https://" + bidder + "-usersync-url.com")
                .build();

        final Usersyncer usersyncer = Usersyncer.of(cookieFamilyName, CookieFamilySource.ROOT, usersyncMethod, null);

        given(bidderCatalog.usersyncerByName(eq(bidder))).willReturn(Optional.of(usersyncer));
        given(bidderCatalog.cookieFamilyName(eq(bidder))).willReturn(Optional.of(cookieFamilyName));
        given(usersyncMethodChooser.choose(eq(usersyncer), eq(bidder))).willReturn(usersyncMethod);
    }

    private Account givenEmptyAccount() {
        return Account.builder().build();
    }

    private Account givenAccount(int limit, int maxLimit) {
        return Account.builder()
                .cookieSync(AccountCookieSyncConfig.of(limit, maxLimit, false))
                .build();
    }

    private void givenCookieSyncService(int limit, int maxLimit) {
        target = new CookieSyncService(
                "https://external-url.com",
                limit,
                maxLimit,
                bidderCatalog,
                hostVendorTcfDefinerService,
                privacyEnforcementService,
                uidsCookieService,
                coopSyncProvider,
                metrics);
    }

    private CookieSyncContext givenCookieSyncContext(
            UnaryOperator<CookieSyncContext.CookieSyncContextBuilder> builderModifier) {

        final CookieSyncContext.CookieSyncContextBuilder builder = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().bidders(singleton("requested-bidder")).build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .uidsCookie(uidsCookie)
                .biddersContext(BiddersContext.builder().requestedBidders(emptySet()).build())
                .usersyncMethodChooser(usersyncMethodChooser)
                .account(givenEmptyAccount());

        return builderModifier.apply(builder).build();
    }

    private CookieSyncRequest givenCookieSyncRequest(String... bidders) {
        return CookieSyncRequest.builder().bidders(Arrays.stream(bidders).collect(Collectors.toSet())).build();
    }
}
