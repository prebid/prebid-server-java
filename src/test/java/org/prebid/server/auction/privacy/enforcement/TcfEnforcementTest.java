package org.prebid.server.auction.privacy.enforcement;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
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
import org.prebid.server.settings.model.AccountPrivacyConfig;

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
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TcfEnforcementTest {

    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock(strictness = LENIENT)
    private UserFpdTcfMask userFpdTcfMask;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private Metrics metrics;

    private TcfEnforcement target;

    @Mock(strictness = LENIENT)
    private BidderAliases aliases;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @BeforeEach
    public void setUp() {
        given(userFpdTcfMask.maskUser(any(), anyBoolean(), anyBoolean(), anySet()))
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

        final Device device = givenDeviceWithPrivacyData();
        final AuctionContext auctionContext = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder0", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder1Alias", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder2", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder3", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder4", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder5", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder6", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder7", givenUserWithPrivacyData(), device));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder0", false, false, false, false, false, true);
        verifyMetric("bidder1", false, false, false, false, false, true);

        verifyMetric("bidder2", false, false, false, false, true, true);
        verifyMetric("bidder3", false, false, false, true, false, true);
        verifyMetric("bidder4", false, false, true, false, false, true);
        verifyMetric("bidder5", false, true, false, false, false, true);
        verifyMetric("bidder6", true, false, false, false, false, true);
        verifyMetric("bidder7", true, false, false, false, false, true);
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenUserHasPrivacyData() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder2", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd)));

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithNoPrivacyData());
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder0", givenUserWithPrivacyData(), givenDeviceWithNoPrivacyData()),
                givenBidderPrivacyResult("bidder1", givenUserWithPrivacyData(), givenDeviceWithNoPrivacyData()),
                givenBidderPrivacyResult("bidder2", givenUserWithPrivacyData(), givenDeviceWithNoPrivacyData()));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder0", false, false, true, false, false, false);
        verifyMetric("bidder1", false, false, false, false, false, false);
        verifyMetric("bidder2", true, false, false, false, false, false);
    }

    @Test
    public void enforceShouldEmitBuyerUidScrubbedMetricsWhenUserHasBuyerUid() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder2", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd),
                "bidder3", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder4", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd),
                "bidder5", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo)));

        final User givenUserWithoutBuyerUid = givenUserWithPrivacyData();

        final User givenUserWithBuyerUid = givenUserWithoutBuyerUid.toBuilder()
                .buyeruid("buyeruid")
                .build();

        final AuctionContext auctionContext = givenAuctionContext(givenDeviceWithNoPrivacyData());
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder0", givenUserWithBuyerUid, givenDeviceWithNoPrivacyData()),
                givenBidderPrivacyResult("bidder1", givenUserWithBuyerUid, givenDeviceWithNoPrivacyData()),
                givenBidderPrivacyResult("bidder2", givenUserWithoutBuyerUid, givenDeviceWithNoPrivacyData()),
                givenBidderPrivacyResult("bidder3", givenUserWithoutBuyerUid, givenDeviceWithPrivacyData()),
                givenBidderPrivacyResult("bidder4", givenUserWithBuyerUid, givenDeviceWithNoPrivacyData()),
                givenBidderPrivacyResult("bidder5", givenUserWithBuyerUid, givenDeviceWithPrivacyData()));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verify(metrics, never()).updateAdapterRequestBuyerUidScrubbedMetrics(eq("bidder0"), any());
        verify(metrics, never()).updateAdapterRequestBuyerUidScrubbedMetrics(eq("bidder1"), any());
        verify(metrics, never()).updateAdapterRequestBuyerUidScrubbedMetrics(eq("bidder2"), any());
        verify(metrics, never()).updateAdapterRequestBuyerUidScrubbedMetrics(eq("bidder3"), any());
        verify(metrics).updateAdapterRequestBuyerUidScrubbedMetrics(eq("bidder4"), any());
        verify(metrics).updateAdapterRequestBuyerUidScrubbedMetrics(eq("bidder5"), any());
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenDeviceHavePrivacyData() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserIds),
                "bidder2", givenEnforcementAction(PrivacyEnforcementAction::setMaskDeviceInfo),
                "bidder3", givenEnforcementAction(PrivacyEnforcementAction::setRemoveUserFpd)));

        final Device device = givenDeviceWithPrivacyData();
        final AuctionContext auctionContext = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder0", givenUserWithNoPrivacyData(), device),
                givenBidderPrivacyResult("bidder1", givenUserWithNoPrivacyData(), device),
                givenBidderPrivacyResult("bidder2", givenUserWithNoPrivacyData(), device),
                givenBidderPrivacyResult("bidder3", givenUserWithNoPrivacyData(), device),
                givenBidderPrivacyResult("bidder4", givenUserWithNoPrivacyData(), device));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder0", false, false, true, false, false, true);
        verifyMetric("bidder1", false, false, false, false, false, true);
        verifyMetric("bidder2", true, false, false, false, false, true);
        verifyMetric("bidder3", false, false, false, false, false, true);
    }

    @Test
    public void enforceShouldEmitExpectedMetricsWhenUserAndDeviceDoNotHavePrivacyData() {
        // give
        givenPrivacyEnforcementActions(Map.of(
                "bidder0", givenEnforcementAction(PrivacyEnforcementAction::setMaskGeo),
                "bidder1", givenEnforcementAction(
                        PrivacyEnforcementAction::setRemoveUserFpd,
                        PrivacyEnforcementAction::setMaskDeviceInfo)));

        final Device device = givenDeviceWithNoPrivacyData();
        final AuctionContext auctionContext = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder0", givenUserWithNoPrivacyData(), device),
                givenBidderPrivacyResult("bidder1", givenUserWithNoPrivacyData(), device));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder0", false, false, false, false, false, false);
        verifyMetric("bidder1", false, false, false, false, false, false);
    }

    @Test
    public void enforceShouldEmitPrivacyLmtMetric() {
        // give
        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final Device device = givenDeviceWithPrivacyData();
        final AuctionContext auctionContext = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder", givenUserWithPrivacyData(), device));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder", false, false, false, false, false, true);
    }

    @Test
    public void enforceShouldNotEmitPrivacyLmtMetricWhenLmtNot1() {
        // give
        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final Device device = givenDeviceWithNoPrivacyData();
        final AuctionContext auctionContext = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder", givenUserWithPrivacyData(), device));

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder", false, false, false, false, false, false);

    }

    @Test
    public void enforceShouldNotEmitPrivacyLmtMetricWhenLmtNotEnforced() {
        // give
        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final Device device = givenDeviceWithPrivacyData();
        final AuctionContext auctionContext = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder", givenUserWithPrivacyData(), device));

        target = new TcfEnforcement(tcfDefinerService, userFpdTcfMask, bidderCatalog, metrics, false);

        // when
        target.enforce(auctionContext, aliases, initialResults);

        // then
        verifyMetric("bidder", false, false, false, false, false, false);

    }

    @Test
    public void enforceShouldMaskUserAndDeviceWhenRestrictionsEnforcedAndLmtNotEnabled() {
        // give
        final User maskedUser = User.builder().id("maskedUser").build();
        final Device maskedDevice = Device.builder().ip("maskedDevice").build();

        given(userFpdTcfMask.maskUser(any(), eq(true), eq(true), eq(singleton("eidException"))))
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

        final Device device = givenDeviceWithNoPrivacyData();
        final AuctionContext context = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder0", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder1", givenUserWithPrivacyData(), device),
                givenBidderPrivacyResult("bidder2", givenUserWithPrivacyData(), device));

        // when
        final List<BidderPrivacyResult> result = target.enforce(context, aliases, initialResults).result();

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

        given(userFpdTcfMask.maskUser(any(), eq(true), eq(true), eq(singleton("eidException"))))
                .willReturn(maskedUser);
        given(userFpdTcfMask.maskDevice(any(), eq(true), eq(true), eq(true)))
                .willReturn(maskedDevice);

        givenPrivacyEnforcementActions(Map.of("bidder", givenEnforcementAction()));

        final Device device = givenDeviceWithPrivacyData();
        final AuctionContext context = givenAuctionContext(device);
        final List<BidderPrivacyResult> initialResults = List.of(
                givenBidderPrivacyResult("bidder", givenUserWithPrivacyData(), device));

        // when
        final List<BidderPrivacyResult> result = target.enforce(context, aliases, initialResults).result();

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

    private AuctionContext givenAuctionContext(Device device) {
        return AuctionContext.builder()
                .activityInfrastructure(activityInfrastructure)
                .bidRequest(BidRequest.builder().device(device).build())
                .requestTypeMetric(MetricName.openrtb2web)
                .account(Account.builder()
                        .privacy(AccountPrivacyConfig.builder().build())
                        .build())
                .privacyContext(PrivacyContext.of(null, TcfContext.empty(), null))
                .build();
    }

    private static BidderPrivacyResult givenBidderPrivacyResult(String bidder, User user, Device device) {
        return BidderPrivacyResult.builder().requestBidder(bidder).user(user).device(device).build();
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
                .eids(singletonList(Eid.builder().build()))
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
        action.setEidExceptions(singleton("eidException"));

        return action;
    }

    private void verifyMetric(String bidder,
                              boolean userFpdRemoved,
                              boolean userIdsRemoved,
                              boolean geoMasked,
                              boolean analyticsBlocked,
                              boolean requestBlocked,
                              boolean lmtEnabled) {

        verify(metrics).updateAuctionTcfAndLmtMetrics(
                eq(activityInfrastructure),
                eq(bidder),
                any(),
                eq(userFpdRemoved),
                eq(userIdsRemoved),
                eq(geoMasked),
                eq(analyticsBlocked),
                eq(requestBlocked),
                eq(lmtEnabled));
    }
}
