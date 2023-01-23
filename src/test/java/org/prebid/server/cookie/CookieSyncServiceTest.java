package org.prebid.server.cookie;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
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
import org.prebid.server.cookie.model.CookieSyncStatus;
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
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountCoopSyncConfig;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
    @Mock
    private RoutingContext routingContext;

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
        givenCookieSyncService(42, 42);

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
        givenCoopSyncBidders("coop-sync-bidder");

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
        givenCoopSyncBidders("coop-sync-bidder");

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        givenAllAllowedTcfResultForBidders("requested-bidder", "coop-sync-bidder");

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
        givenCoopSyncBidders("coop-sync-bidder");

        given(bidderCatalog.isValidName("disabled-bidder")).willReturn(true);
        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        givenAllAllowedTcfResultForBidders("requested-bidder", "coop-sync-bidder");

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
        givenCoopSyncBidders("coop-sync-bidder");

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder", "bidder-without-usersync");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        givenAllAllowedTcfResultForBidders("requested-bidder", "coop-sync-bidder");

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
        givenCoopSyncBidders("coop-sync-bidder");

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        given(usersyncMethodChooser.choose(any(), eq("coop-sync-bidder"))).willReturn(null);

        givenAllAllowedTcfResultForBidders("requested-bidder", "coop-sync-bidder");

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
        givenCoopSyncBidders("coop-sync-bidder");

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

    @Test
    public void processContextShouldFilterInSyncBidders() {
        // given
        givenCoopSyncBidders("coop-sync-bidder");

        givenValidActiveBidders("requested-bidder", "coop-sync-bidder");
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        givenAllAllowedTcfResultForBidders("requested-bidder", "coop-sync-bidder");

        given(uidsCookie.hasLiveUidFrom("requested-bidder-cookie-family")).willReturn(true);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(builder ->
                builder.cookieSyncRequest(givenCookieSyncRequest("requested-bidder")));

        // when
        final Future<CookieSyncContext> result = target.processContext(cookieSyncContext);

        // then
        assertThat(result).isSucceeded()
                .unwrap()
                .extracting(CookieSyncContext::getBiddersContext)
                .extracting(BiddersContext::rejectedBidders)
                .isEqualTo(Map.of("requested-bidder", RejectionReason.ALREADY_IN_SYNC));
    }

    @Test
    public void prepareResponseShouldReturnOkStatusWhenUidsCookieHasLiveUids() {
        // given
        given(uidsCookie.hasLiveUids()).willReturn(true);
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(UnaryOperator.identity());

        // when
        final CookieSyncResponse result = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(result.getStatus()).isEqualTo(CookieSyncStatus.OK);
    }

    @Test
    public void prepareResponseShouldReturnNoCookieStatusWhenUidsCookieHasNoLiveUids() {
        // given
        given(uidsCookie.hasLiveUids()).willReturn(false);
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(UnaryOperator.identity());

        // when
        final CookieSyncResponse result = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(result.getStatus()).isEqualTo(CookieSyncStatus.NO_COOKIE);
    }

    @Test
    public void prepareResponseShouldLimitResponseStatuses() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.limit(1),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void prepareResponseShouldFavourRequest() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.limit(1),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getBidder)
                .containsExactly("requested-bidder-cookie-family");
    }

    @Test
    public void prepareResponseShouldFavourCoopSyncAfterRequest() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.limit(1),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(Map.of("requested-bidder", RejectionReason.REJECTED_BY_TCF))
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getBidder)
                .containsExactly("coop-sync-bidder-cookie-family");
    }

    @Test
    public void prepareResponseShouldNotReturnErrorsWhenDebugFalse() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.limit(1),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(Map.of("requested-bidder", RejectionReason.REJECTED_BY_TCF))
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getBidder)
                .containsExactly("coop-sync-bidder-cookie-family");
    }

    @Test
    public void prepareResponseShouldNotLimitErrorsWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder
                        .limit(1)
                        .debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(Map.of("requested-bidder", RejectionReason.REJECTED_BY_TCF))
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2);
    }

    @Test
    public void prepareResponseShouldReturnInvalidBidderErrorOnlyForRequestedBiddersWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.INVALID_BIDDER,
                "coop-sync-bidder", RejectionReason.INVALID_BIDDER);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons)
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                .containsExactly(errorStatus("requested-bidder-cookie-family", "Unsupported bidder"));
    }

    @Test
    public void prepareResponseShouldReturnDisabledBidderErrorOnlyForRequestedBiddersWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.DISABLED_BIDDER,
                "coop-sync-bidder", RejectionReason.DISABLED_BIDDER);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons)
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                .containsExactly(errorStatus("requested-bidder-cookie-family", "Disabled bidder"));
    }

    @Test
    public void prepareResponseShouldReturnTcfRejectedErrorForCoopSyncAndRequestedBiddersWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.REJECTED_BY_TCF,
                "coop-sync-bidder", RejectionReason.REJECTED_BY_TCF);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons)
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus()).containsExactlyInAnyOrder(
                errorStatus("requested-bidder-cookie-family", "Rejected by TCF"),
                errorStatus("coop-sync-bidder-cookie-family", "Rejected by TCF"));
    }

    @Test
    public void prepareResponseShouldReturnCcpaRejectedErrorForCoopSyncAndRequestedBiddersWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.REJECTED_BY_CCPA,
                "coop-sync-bidder", RejectionReason.REJECTED_BY_CCPA);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons)
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus()).containsExactlyInAnyOrder(
                errorStatus("requested-bidder-cookie-family", "Rejected by CCPA"),
                errorStatus("coop-sync-bidder-cookie-family", "Rejected by CCPA"));
    }

    @Test
    public void prepareResponseShouldReturnUnconfiguredUsersyncErrorOnlyForRequestedBiddersWhenDebugTrue() {
        // given
        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.UNCONFIGURED_USERSYNC,
                "coop-sync-bidder", RejectionReason.UNCONFIGURED_USERSYNC);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                // only here we will end up with bidder code in bidder field instead of cookie family name
                .containsExactly(errorStatus("requested-bidder", "No sync config"));
    }

    @Test
    public void prepareResponseShouldReturnFilterRejectedErrorForCoopSyncAndRequestedBiddersWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.REJECTED_BY_FILTER,
                "coop-sync-bidder", RejectionReason.REJECTED_BY_FILTER);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons)
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus()).containsExactlyInAnyOrder(
                errorStatus("requested-bidder-cookie-family", "Rejected by request filter"),
                errorStatus("coop-sync-bidder-cookie-family", "Rejected by request filter"));
    }

    @Test
    public void prepareResponseShouldReturnAlreadyInSyncErrorOnlyForRequestedBiddersWhenDebugTrue() {
        // given
        givenUsersyncersForBidders("requested-bidder", "coop-sync-bidder");

        final Map<String, UsersyncMethod> bidderUsersyncMethods = Map.of(
                "requested-bidder", givenUsersyncMethod("requested-bidder"),
                "coop-sync-bidder", givenUsersyncMethod("coop-sync-bidder"));

        final Map<String, RejectionReason> biddersRejectionReasons = Map.of(
                "requested-bidder", RejectionReason.ALREADY_IN_SYNC,
                "coop-sync-bidder", RejectionReason.ALREADY_IN_SYNC);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("requested-bidder"))
                        .coopSyncBidders(singleton("coop-sync-bidder"))
                        .rejectedBidders(biddersRejectionReasons)
                        .bidderUsersyncMethod(bidderUsersyncMethods));

        // when
        final CookieSyncResponse cookieSyncResponse = target.prepareResponse(cookieSyncContext);

        // then
        assertThat(cookieSyncResponse.getBidderStatus())
                .containsExactly(errorStatus("requested-bidder-cookie-family", "Already in sync"));
    }

    @Test
    public void prepareResponseShouldReturnWarningForAliasesSyncedAsRootCookieFamilyWhenDebugTrue() {
        // given
        given(bidderCatalog.isValidName("alias")).willReturn(true);
        given(bidderCatalog.isActive("alias")).willReturn(true);
        given(bidderCatalog.isAlias("alias")).willReturn(true);
        givenUsersyncerForBidder("alias", "root-cookie-family", CookieFamilySource.ROOT);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("alias"))
                        .bidderUsersyncMethod(Map.of("alias", givenUsersyncMethod("alias"))));

        // when
        final CookieSyncResponse result = target.prepareResponse(cookieSyncContext);

        // then
        final BidderUsersyncStatus warningStatus = errorStatus("alias", "synced as root-cookie-family");
        final BidderUsersyncStatus validStatus = BidderUsersyncStatus.builder()
                .bidder("root-cookie-family")
                .noCookie(true)
                .usersync(UsersyncInfo.of("https://alias-usersync-url.com", UsersyncMethodType.IFRAME, false))
                .build();

        assertThat(result.getBidderStatus()).containsExactlyInAnyOrder(validStatus, warningStatus);
    }

    @Test
    public void prepareResponseShouldNotReturnWarningForAliasesSyncedAsAliasCookieFamilyWhenDebugFalse() {
        // given
        given(bidderCatalog.isValidName("alias")).willReturn(true);
        given(bidderCatalog.isActive("alias")).willReturn(true);
        given(bidderCatalog.isAlias("alias")).willReturn(true);
        givenUsersyncerForBidder("alias", "alias-cookie-family", CookieFamilySource.ALIAS);

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("alias"))
                        .bidderUsersyncMethod(Map.of("alias", givenUsersyncMethod("alias"))));

        // when
        final CookieSyncResponse result = target.prepareResponse(cookieSyncContext);

        // then
        final BidderUsersyncStatus status = BidderUsersyncStatus.builder()
                .bidder("alias-cookie-family")
                .noCookie(true)
                .usersync(UsersyncInfo.of("https://alias-usersync-url.com", UsersyncMethodType.IFRAME, false))
                .build();

        assertThat(result.getBidderStatus()).containsExactly(status);
    }

    @Test
    public void prepareResponseShouldReturnErrorForBiddersThatWereNotIncludedInResponseDueToLimitWhenDebugTrue() {
        // given
        givenValidActiveBidders("bidder1", "bidder2");
        givenUsersyncersForBidders("bidder1", "bidder2");

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                cookieSyncContextBuilder -> cookieSyncContextBuilder.debug(true).limit(1),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(new LinkedHashSet<>(List.of("bidder1", "bidder2"))) // to preserve order
                        .bidderUsersyncMethod(
                                Map.of("bidder1", givenUsersyncMethod("bidder1"),
                                        "bidder2", givenUsersyncMethod("bidder2"))));

        // when
        final CookieSyncResponse result = target.prepareResponse(cookieSyncContext);

        // then
        final BidderUsersyncStatus warningStatus = errorStatus("bidder2-cookie-family", "limit reached");
        final BidderUsersyncStatus validStatus = BidderUsersyncStatus.builder()
                .bidder("bidder1-cookie-family")
                .noCookie(true)
                .usersync(UsersyncInfo.of("https://bidder1-usersync-url.com", UsersyncMethodType.IFRAME, false))
                .build();

        assertThat(result.getBidderStatus()).containsExactlyInAnyOrder(validStatus, warningStatus);
    }

    @Test
    public void prepareResponseShouldReturnCustomUsersyncUrlForHostCookieSync() {
        // given
        givenValidActiveBidder("host-bidder");
        givenUsersyncersForBidders("host-bidder");

        given(uidsCookieService.hostCookieUidToSync(routingContext, "host-bidder-cookie-family"))
                .willReturn("bogus");

        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(
                UnaryOperator.identity(),
                biddersContextBuilder -> biddersContextBuilder
                        .requestedBidders(singleton("host-bidder"))
                        .bidderUsersyncMethod(Map.of("host-bidder", givenUsersyncMethod("alias"))));

        // when
        final CookieSyncResponse result = target.prepareResponse(cookieSyncContext);

        // then
        final String expectedUrl = """
                https://external-url.com/setuid\
                ?bidder=host-bidder-cookie-family\
                &gdpr=gdpr\
                &gdpr_consent=consent-string\
                &us_privacy=\
                &gpp=\
                &gpp_sid=\
                &f=b\
                &uid=bogus""";
        final BidderUsersyncStatus status = BidderUsersyncStatus.builder()
                .noCookie(true)
                .bidder("host-bidder-cookie-family")
                .usersync(UsersyncInfo.of(expectedUrl, UsersyncMethodType.IFRAME, false))
                .build();

        assertThat(result.getBidderStatus()).containsExactly(status);
    }

    private PrivacyContext givenAllAllowedPrivacyContext() {
        return givenPrivacyContext(TcfContext.builder().inGdprScope(false).build());
    }

    private PrivacyContext givenPrivacyContext(TcfContext tcfContext) {
        final Privacy privacy = Privacy.builder()
                .gdpr("gdpr")
                .consentString("consent-string")
                .ccpa(Ccpa.EMPTY)
                .coppa(1)
                .build();

        return PrivacyContext.of(privacy, tcfContext);
    }

    private void givenValidActiveBidders(String... bidders) {
        Arrays.stream(bidders).forEach(this::givenValidActiveBidder);
    }

    private void givenValidActiveBidder(String bidder) {
        given(bidderCatalog.isValidName(bidder)).willReturn(true);
        given(bidderCatalog.isActive(bidder)).willReturn(true);
    }

    private void givenCoopSyncBidders(String... bidders) {
        given(coopSyncProvider.coopSyncBidders(any())).willReturn(Arrays.stream(bidders).collect(Collectors.toSet()));
    }

    private void givenAllAllowedTcfResultForBidders(String... bidders) {
        final Map<String, PrivacyEnforcementAction> actions = Arrays.stream(bidders)
                .collect(Collectors.toMap(identity(), ignored -> PrivacyEnforcementAction.allowAll()));

        final TcfResponse<String> tcfResponse = TcfResponse.of(true, actions, "country");

        given(hostVendorTcfDefinerService.resultForBidderNames(anySet(), any(), any()))
                .willReturn(Future.succeededFuture(tcfResponse));
    }

    private void givenUsersyncersForBidders(String... bidders) {
        Arrays.stream(bidders).forEach(this::givenUsersyncerForBidder);
    }

    private void givenUsersyncerForBidder(String bidder) {
        givenUsersyncerForBidder(bidder, bidder + "-cookie-family", CookieFamilySource.ROOT);
    }

    private void givenUsersyncerForBidder(String bidder,
                                          String cookieFamilyName,
                                          CookieFamilySource cookieFamilySource) {

        final UsersyncMethod usersyncMethod = givenUsersyncMethod(bidder);
        final Usersyncer usersyncer = Usersyncer.of(cookieFamilyName, cookieFamilySource, usersyncMethod, null);

        given(bidderCatalog.usersyncerByName(eq(bidder))).willReturn(Optional.of(usersyncer));
        given(bidderCatalog.cookieFamilyName(eq(bidder))).willReturn(Optional.of(cookieFamilyName));
        given(usersyncMethodChooser.choose(eq(usersyncer), eq(bidder))).willReturn(usersyncMethod);
    }

    private static UsersyncMethod givenUsersyncMethod(String bidder) {
        return UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl("https://" + bidder + "-usersync-url.com")
                .build();
    }

    private static Account givenEmptyAccount() {
        return Account.builder().build();
    }

    private static Account givenAccount(int limit, int maxLimit) {
        return Account.builder()
                .cookieSync(AccountCookieSyncConfig.of(limit, maxLimit, null, AccountCoopSyncConfig.of(false)))
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
            UnaryOperator<CookieSyncContext.CookieSyncContextBuilder> cookieSyncContextModifier) {

        return givenCookieSyncContext(cookieSyncContextModifier, UnaryOperator.identity());
    }

    private CookieSyncContext givenCookieSyncContext(
            UnaryOperator<CookieSyncContext.CookieSyncContextBuilder> cookieSyncContextModifier,
            UnaryOperator<BiddersContext.BiddersContextBuilder> buildersContextModifier) {

        final BiddersContext biddersContext = buildersContextModifier
                .apply(BiddersContext.builder().requestedBidders(emptySet()))
                .build();

        final CookieSyncContext.CookieSyncContextBuilder builder = CookieSyncContext.builder()
                .cookieSyncRequest(CookieSyncRequest.builder().bidders(singleton("requested-bidder")).build())
                .privacyContext(givenAllAllowedPrivacyContext())
                .routingContext(routingContext)
                .uidsCookie(uidsCookie)
                .biddersContext(biddersContext)
                .usersyncMethodChooser(usersyncMethodChooser)
                .limit(Integer.MAX_VALUE)
                .account(givenEmptyAccount());

        return cookieSyncContextModifier.apply(builder).build();
    }

    private static CookieSyncRequest givenCookieSyncRequest(String... bidders) {
        return CookieSyncRequest.builder().bidders(Arrays.stream(bidders).collect(Collectors.toSet())).build();
    }

    private static BidderUsersyncStatus errorStatus(String bidder, String error) {
        return BidderUsersyncStatus.builder()
                .bidder(bidder)
                .error(error)
                .build();
    }
}
