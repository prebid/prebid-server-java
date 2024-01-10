package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdTcfMask;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeEid;
import org.prebid.server.settings.model.Purposes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TcfEnforcementTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock
    private UserFpdTcfMask userFpdTcfMask;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private Metrics metrics;

    private TcfEnforcement target;

    @Mock
    private BidderAliases aliases;

    @Before
    public void setUp() {
        given(userFpdTcfMask.maskUser(any(), anyBoolean(), anyBoolean(), anyBoolean(), anySet()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userFpdTcfMask.maskDevice(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .willAnswer(invocation -> invocation.getArgument(0));

        target = new TcfEnforcement(tcfDefinerService, userFpdTcfMask, bidderCatalog, metrics, true);

        given(aliases.resolveBidder(anyString()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void enforceShouldReturnResultForVendorsIds() {
        // given
        final Set<Integer> vendorsIds = Collections.emptySet();
        final TcfContext tcfContext = TcfContext.empty();
        given(tcfDefinerService.resultForVendorIds(same(vendorsIds), same(tcfContext)))
                .willReturn(Future.succeededFuture(TcfResponse.of(
                        null,
                        Map.of(1, PrivacyEnforcementAction.allowAll()),
                        null)));

        // when
        final Map<Integer, PrivacyEnforcementAction> result = target.enforce(vendorsIds, tcfContext).result();

        // then
        assertThat(result).containsAllEntriesOf(Map.of(1, PrivacyEnforcementAction.allowAll()));
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenUserAndDeviceHavePrivacyData() {
        // give
        given(aliases.resolveBidder(eq("bidder1Alias"))).willReturn("bidder1");

        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(),
                "bidder1Alias", givenEnforcementAction(),
                "bidder2", givenEnforcementAction(
                        PrivacyEnforcementAction::setBlockBidderRequest,
                        PrivacyEnforcementAction::setRemoveUserFpd,
                        PrivacyEnforcementAction::setMaskDeviceInfo,
                        PrivacyEnforcementAction::setRemoveUserIds,
                        PrivacyEnforcementAction::setMaskGeo,
                        PrivacyEnforcementAction::setBlockAnalyticsReport),

                "bidder3", givenEnforcementAction(PrivacyEnforcementAction::setBlockAnalyticsReport),
                "bidder4", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder5", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserIds),
                "bidder6", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder7", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd)));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithPrivacyData());
        final Map<String, User> bidderToUser = Map.of(
                "bidder0", givenUserWithPrivacyData(),
                "bidder1Alias", givenUserWithPrivacyData(),
                "bidder2", givenUserWithPrivacyData(),
                "bidder3", givenUserWithPrivacyData(),
                "bidder4", givenUserWithPrivacyData(),
                "bidder5", givenUserWithPrivacyData(),
                "bidder6", givenUserWithPrivacyData(),
                "bidder7", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of();

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verifyMetric("bidder0", false, false, false, false, false);
        verifyMetric("bidder1", false, false, false, false, false);

        verifyMetric("bidder2", false, false, false, false, true);
        verifyMetric("bidder3", false, false, false, true, false);
        verifyMetric("bidder4", false, false, true, false, false);
        verifyMetric("bidder5", false, true, false, false, false);
        verifyMetric("bidder6", true, false, false, false, false);
        verifyMetric("bidder7", true, false, false, false, false);
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenUserHasPrivacyData() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder2", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd)));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithNoPrivacyData());
        final Map<String, User> bidderToUser = Map.of(
                "bidder0", givenUserWithPrivacyData(),
                "bidder1", givenUserWithPrivacyData(),
                "bidder2", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of();

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verifyMetric("bidder0", false, false, true, false, false);
        verifyMetric("bidder1", false, false, false, false, false);
        verifyMetric("bidder2", true, false, false, false, false);
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenDeviceHavePrivacyData() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserIds),
                "bidder2", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder3", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd)));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithPrivacyData());
        final Map<String, User> bidderToUser = Map.of(
                "bidder0", givenUserWithNoPrivacyData(),
                "bidder1", givenUserWithNoPrivacyData(),
                "bidder2", givenUserWithNoPrivacyData(),
                "bidder3", givenUserWithNoPrivacyData(),
                "bidder4", givenUserWithNoPrivacyData());
        final Set<String> bidders = Set.of();

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verifyMetric("bidder0", false, false, true, false, false);
        verifyMetric("bidder1", false, false, false, false, false);
        verifyMetric("bidder2", true, false, false, false, false);
        verifyMetric("bidder3", false, false, false, false, false);
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenUserAndDeviceDoNotHavePrivacyData() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(
                        PrivacyEnforcementAction::setRemoveUserFpd,
                        PrivacyEnforcementAction::setMaskDeviceInfo)));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithNoPrivacyData());
        final Map<String, User> bidderToUser = Map.of(
                "bidder0", givenUserWithNoPrivacyData(),
                "bidder1", givenUserWithNoPrivacyData());
        final Set<String> bidders = Set.of();

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verifyMetric("bidder0", false, false, false, false, false);
        verifyMetric("bidder1", false, false, false, false, false);
    }

    @Test
    public void enforceShouldEmitPrivacyLmtMetric() {
        // give
        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithPrivacyData());
        final Map<String, User> bidderToUser = Map.of("bidder", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of();

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verify(metrics).updatePrivacyLmtMetric();
    }

    @Test
    public void enforceShouldNotEmitPrivacyLmtMetricWhenLmtNot1() {
        // give
        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithNoPrivacyData());
        final Map<String, User> bidderToUser = Map.of("bidder", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of();

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verify(metrics, times(0)).updatePrivacyLmtMetric();
    }

    @Test
    public void enforceShouldNotEmitPrivacyLmtMetricWhenLmtNotEnforced() {
        // give
        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithPrivacyData());
        final Map<String, User> bidderToUser = Map.of("bidder", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of();

        target = new TcfEnforcement(tcfDefinerService, userFpdTcfMask, bidderCatalog, metrics, false);

        // when
        target.enforce(auctionContext, bidderToUser, bidders, aliases);

        // then
        verify(metrics, times(0)).updatePrivacyLmtMetric();
    }

    @Test
    public void enforceShouldMaskUserAndDeviceWhenRestrictionsEnforcedAndLmtNotEnabled() {
        // give
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(userFpdTcfMask.maskUser(any(), eq(true), eq(true), eq(true), eq(singleton("eidException"))))
                .willReturn(maskedUser);
        given(userFpdTcfMask.maskDevice(any(), eq(true), eq(true), eq(true)))
                .willReturn(maskedDevice);

        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(
                        PrivacyEnforcementAction::setBlockBidderRequest,
                        PrivacyEnforcementAction::setBlockAnalyticsReport),
                "bidder1", givenEnforcementAction(
                        PrivacyEnforcementAction::setRemoveUserFpd,
                        PrivacyEnforcementAction::setMaskDeviceInfo,
                        PrivacyEnforcementAction::setRemoveUserIds,
                        PrivacyEnforcementAction::setMaskDeviceIp,
                        PrivacyEnforcementAction::setMaskGeo,
                        PrivacyEnforcementAction::setBlockAnalyticsReport),
                "bidder2", givenEnforcementAction()));

        final AuctionContext context = givenAuctionContext(givenDeviceWithNoPrivacyData());
        final Map<String, User> bidderToUser = Map.of(
                "bidder0", givenUserWithPrivacyData(),
                "bidder1", givenUserWithPrivacyData(),
                "bidder2", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of("bidder0", "bidder1", "bidder2");

        // when
        final List<BidderPrivacyResult> result = target.enforce(context, bidderToUser, bidders, aliases).result();

        // then
        assertThat(result).containsExactlyInAnyOrder(
                BidderPrivacyResult.builder()
                        .requestBidder("bidder0")
                        .blockedRequestByTcf(true)
                        .blockedAnalyticsByTcf(true)
                        .build(),
                BidderPrivacyResult.builder()
                        .requestBidder("bidder1")
                        .blockedRequestByTcf(false)
                        .blockedAnalyticsByTcf(true)
                        .user(maskedUser)
                        .device(maskedDevice)
                        .build(),
                BidderPrivacyResult.builder()
                        .requestBidder("bidder2")
                        .blockedRequestByTcf(false)
                        .blockedAnalyticsByTcf(false)
                        .user(givenUserWithPrivacyData())
                        .device(givenDeviceWithNoPrivacyData())
                        .build());
    }

    @Test
    public void enforceShouldMaskUserAndDeviceWhenRestrictionsNotEnforcedAndLmtEnabled() {
        // give
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(userFpdTcfMask.maskUser(any(), eq(true), eq(true), eq(true), eq(singleton("eidException"))))
                .willReturn(maskedUser);
        given(userFpdTcfMask.maskDevice(any(), eq(true), eq(true), eq(true)))
                .willReturn(maskedDevice);

        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final AuctionContext context = givenAuctionContext(givenDeviceWithPrivacyData());
        final Map<String, User> bidderToUser = Map.of("bidder", givenUserWithPrivacyData());
        final Set<String> bidders = Set.of("bidder");

        // when
        final List<BidderPrivacyResult> result = target.enforce(context, bidderToUser, bidders, aliases).result();

        // then
        assertThat(result).containsExactly(
                BidderPrivacyResult.builder()
                        .requestBidder("bidder")
                        .blockedRequestByTcf(false)
                        .blockedAnalyticsByTcf(false)
                        .user(maskedUser)
                        .device(maskedDevice)
                        .build());
    }

    private void givenPrivacyEnforcementActions(Map<String, PrivacyEnforcementAction> actions) {
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(null, actions, null)));
    }

    private static AuctionContext givenAuctionContext(Device device) {
        return AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(device).build())
                .requestTypeMetric(MetricName.openrtb2web)
                .account(Account.builder()
                        .privacy(AccountPrivacyConfig.of(
                                AccountGdprConfig.builder()
                                        .purposes(Purposes.builder()
                                                .p4(Purpose.of(
                                                        null,
                                                        null,
                                                        null,
                                                        PurposeEid.of(null, true, singleton("eidException"))))
                                                .build())
                                        .build(),
                                null,
                                null,
                                null))
                        .build())
                .privacyContext(PrivacyContext.of(null, TcfContext.empty(), null))
                .build();
    }

    private static Device givenDeviceWithPrivacyData() {
        return Device.builder()
                .ip("originalDevice")
                .ifa("ifa")
                .geo(Geo.builder().build())
                .lmt(1)
                .build();
    }

    private static Device givenDeviceWithNoPrivacyData() {
        return Device.builder().ip("originalDevice").build();
    }

    private static User givenUserWithPrivacyData() {
        return User.builder()
                .id("originalUser")
                .eids(singletonList(Eid.of(null, null, null)))
                .geo(Geo.builder().build())
                .build();
    }

    private static User givenUserWithNoPrivacyData() {
        return User.builder().build();
    }

    @SafeVarargs
    private static PrivacyEnforcementAction givenEnforcementAction(
            BiConsumer<PrivacyEnforcementAction, Boolean>... restrictions) {

        final PrivacyEnforcementAction action = PrivacyEnforcementAction.allowAll();
        for (BiConsumer<PrivacyEnforcementAction, Boolean> restriction : restrictions) {
            restriction.accept(action, true);
        }

        return action;
    }

    private void verifyMetric(String bidder,
                              boolean userFpdRemoved,
                              boolean userIdsRemoved,
                              boolean geoMasked,
                              boolean analyticsBlocked,
                              boolean requestBlocked) {

        verify(metrics).updateAuctionTcfMetrics(
                eq(bidder),
                any(),
                eq(userFpdRemoved),
                eq(userIdsRemoved),
                eq(geoMasked),
                eq(analyticsBlocked),
                eq(requestBlocked));
    }
}
