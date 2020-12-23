package org.prebid.server.auction;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;

import java.util.Arrays;
import java.util.HashSet;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class PrivacyAnonymizationServiceTest {

    private static final String BIDDER_NAME = "someBidder";
    private static final String BUYER_UID = "uidval";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private IpAddressHelper ipAddressHelper;

    @Mock
    private Metrics metrics;

    @Mock
    private BidderAliases bidderAliases;

    private PrivacyAnonymizationService privacyAnonymizationService;

    @Before
    public void setUp() {
        given(ipAddressHelper.maskIpv4(anyString())).willReturn("192.168.0.0");
        given(ipAddressHelper.anonymizeIpv6(eq("2001:0db8:85a3:0000:0000:8a2e:0370:7334")))
                .willReturn("2001:0db8:85a3:0000::");
        privacyAnonymizationService = new PrivacyAnonymizationService(false, ipAddressHelper, metrics);
    }

    @Test
    public void maskCoppaShouldMaskDeviceAndUser() {
        // given
        final Device device = notMaskedDevice();
        final User user = notMaskedUser(notMaskedExtUser());

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskCoppa(user, device, BIDDER_NAME);

        // then
        assertThat(bidderPrivacyResult.getDevice()).isEqualTo(deviceCoppaMasked());
        assertThat(bidderPrivacyResult.getUser()).isEqualTo(userCoppaMasked(extUserIdsMasked()));
        assertThat(bidderPrivacyResult.getDebugLog()).contains(
                "Geolocation and address were removed from request to bidder according to COPPA policy.",
                "User demographics were removed from request to bidder according to COPPA policy.",
                "Device IPs were masked in request to bidder according to COPPA policy.",
                "User ids were removed from request to bidder according to COPPA policy.",
                "Device ids were removed form request to bidder according to COPPA policy.");
    }

    @Test
    public void maskCcpaShouldMaskDeviceAndUser() {
        // given
        final Device device = notMaskedDevice();
        final User user = notMaskedUser(notMaskedExtUser());

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskCoppa(user, device, BIDDER_NAME);

        // then
        assertThat(bidderPrivacyResult.getDevice()).isEqualTo(deviceCoppaMasked());
        assertThat(bidderPrivacyResult.getUser()).isEqualTo(userCoppaMasked(extUserIdsMasked()));
        assertThat(bidderPrivacyResult.getDebugLog()).contains(
                "Geolocation and address were removed from request to bidder according to COPPA policy.",
                "User demographics were removed from request to bidder according to COPPA policy.",
                "Device IPs were masked in request to bidder according to COPPA policy.",
                "User ids were removed from request to bidder according to COPPA policy.",
                "Device ids were removed form request to bidder according to COPPA policy.");
    }

    @Test
    public void maskTcfShouldReturnNotMaskedResultWhenLmtIsNullAndEnforcementAllowedAll() {
        // given
        final Device notMaskedDevice = notMaskedDevice();
        final User notMaskedUser = notMaskedUser(notMaskedExtUser());
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskTcf(notMaskedUser, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                        privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .device(notMaskedDevice)
                .user(notMaskedUser)
                .debugLog(emptySet())
                .build());
    }

    @Test
    public void maskTcfShouldReturnResultWithoutUserAndDeviceAndAddLogWhenRequestRestricted() {
        // given
        final Device notMaskedDevice = notMaskedDevice();
        final User notMaskedUser = notMaskedUser(notMaskedExtUser());
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .blockBidderRequest(true).build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskTcf(notMaskedUser, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                        privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .blockedRequestByTcf(true)
                .debugLog(singleton("Bidder someBidder was deprecated by TCF privacy policy."))
                .build());
    }

    @Test
    public void maskTcfShouldSetBlockedAnalyticsFlag() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .blockAnalyticsReport(true).build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService.maskTcf(null, null,
                BIDDER_NAME, bidderAliases, MetricName.openrtb2web, privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .blockedAnalyticsByTcf(true)
                .debugLog(singleton("Bidder someBidder analytics was deprecated by TCF privacy policy."))
                .build());
    }

    @Test
    public void maskTcfShouldMaskGeoDeviceAndUserWhenMaskGeoIsTrue() {
        // given
        final Device notMaskedDevice = notMaskedDevice();
        final User notMaskedUser = notMaskedUser();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .maskGeo(true)
                .build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskTcf(notMaskedUser, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                        privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .device(notMaskedDevice.toBuilder().geo(geoTcfMasked()).build())
                .user(notMaskedUser.toBuilder().geo(geoTcfMasked()).build())
                .debugLog(singleton("Geolocation was masked in request to bidder according to TCF policy."))
                .build());
    }

    @Test
    public void maskTcfShouldMaskUserIdWhenFlagIsRemoveUserIdsTrue() {
        // given
        final User notMaskedUser = notMaskedUser();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .removeUserIds(true)
                .build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService.maskTcf(notMaskedUser, null,
                BIDDER_NAME, bidderAliases, MetricName.openrtb2web, privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(notMaskedUser.toBuilder().id(null).buyeruid(null).ext(maskExtUser()).build())
                .debugLog(singleton("User ids were removed from request to bidder according to TCF policy."))
                .build());
    }

    @Test
    public void maskTcfShouldMaskDeviceIdWhenMaskIpFlagIsTrue() {
        // given
        final Device notMaskedDevice = notMaskedDevice();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .maskDeviceIp(true)
                .build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService.maskTcf(null, notMaskedDevice,
                BIDDER_NAME, bidderAliases, MetricName.openrtb2web, privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .device(notMaskedDevice.toBuilder().ip("192.168.0.0").ipv6("2001:0db8:85a3:0000::").build())
                .debugLog(singleton("Device IPs were masked in request to bidder according to TCF policy."))
                .build());
    }

    @Test
    public void maskTcfShouldMaskDeviceIdsWhenMaskDeviceInfoIsTrue() {
        // given
        final Device notMaskedDevice = notMaskedDevice();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .maskDeviceInfo(true)
                .build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService.maskTcf(null, notMaskedDevice,
                BIDDER_NAME, bidderAliases, MetricName.openrtb2web, privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .device(notMaskedDevice.toBuilder().ifa(null).macsha1(null).macmd5(null)
                        .dpidsha1(null).dpidmd5(null).didsha1(null).didmd5(null).build())
                .debugLog(singleton("Device ids were removed form request to bidder according to TCF policy."))
                .build());
    }

    @Test
    public void maskTcfShouldMaskEverythingWhenPrivacyRestrictsAllExceptRequest() {
        // given
        final Device notMaskedDevice = notMaskedDevice();
        final User notMaskedUser = notMaskedUser();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll().toBuilder()
                .blockBidderRequest(false)
                .build();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskTcf(notMaskedUser, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                        privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(userTcfMasked())
                .device(deviceTcfMasked())
                .blockedAnalyticsByTcf(true)
                .debugLog(new HashSet<>(Arrays.asList(
                        "Geolocation was masked in request to bidder according to TCF policy.",
                        "Bidder someBidder analytics was deprecated by TCF privacy policy.",
                        "User ids were removed from request to bidder according to TCF policy.",
                        "Device ids were removed form request to bidder according to TCF policy.",
                        "Device IPs were masked in request to bidder according to TCF policy.")))
                .build());
    }

    @Test
    public void maskTcfShouldMaskEverythingWhenPrivacyAllowedAllButLmtEnforceTrueAndLmtDeviceOne() {
        // given
        privacyAnonymizationService = new PrivacyAnonymizationService(true, ipAddressHelper, metrics);
        final Device notMaskedDevice = notMaskedDevice().toBuilder().lmt(1).build();
        final User notMaskedUser = notMaskedUser();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();

        // when
        final BidderPrivacyResult bidderPrivacyResult = privacyAnonymizationService
                .maskTcf(notMaskedUser, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                        privacyEnforcementAction);

        // then
        assertThat(bidderPrivacyResult).isEqualTo(BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(userTcfMasked())
                .device(deviceTcfMasked().toBuilder().lmt(1).build())
                .debugLog(new HashSet<>(Arrays.asList(
                        "Geolocation was masked in request to bidder according to TCF policy.",
                        "User ids were removed from request to bidder according to TCF policy.",
                        "Device ids were removed form request to bidder according to TCF policy.",
                        "Device IPs were masked in request to bidder according to TCF policy.")))
                .build());
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithFalseAndTrueForRequestBlockedWhenRequestIsBlocked() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final Device notMaskedDevice = notMaskedDevice();
        final User notMaskedUser = notMaskedUser();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll()
                .toBuilder().blockBidderRequest(true).build();

        // when
        privacyAnonymizationService
                .maskTcf(notMaskedUser, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                        privacyEnforcementAction);
        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(false),
                eq(false), eq(true));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithTrueForUserIdRemovedWhenUserIdRemoveTrueAndUserHasId() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final User notMaskedUser = User.builder().id("id").build();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .removeUserIds(true).build();

        // when
        privacyAnonymizationService.maskTcf(notMaskedUser, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(true), eq(false),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithTrueForUserIdRemovedWhenUserIdRemoveTrueAndUserBuyeruid() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final User notMaskedUser = User.builder().buyeruid("buyeruid").build();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .removeUserIds(true).build();

        // when
        privacyAnonymizationService.maskTcf(notMaskedUser, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(true), eq(false),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithTrueForUserIdRemovedWhenUserIdRemoveTrueAndUserEid() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final User notMaskedUser = User.builder().ext(ExtUser.builder()
                .eids(singletonList(ExtUserEid.of(null, null, null, null))).build()).build();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .removeUserIds(true).build();

        // when
        privacyAnonymizationService.maskTcf(notMaskedUser, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(true), eq(false),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithFalseForUserIdRemovedWhenUserIdRemoveTrueAndUserIsNull() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .removeUserIds(true).build();

        // when
        privacyAnonymizationService.maskTcf(null, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(false),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithTrueForMaskGeoWhenMaskGeoTrueAndUserHasGeo() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final User notMaskedUser = User.builder().geo(Geo.builder().build()).build();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .maskGeo(true).build();

        // when
        privacyAnonymizationService.maskTcf(notMaskedUser, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(true),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithTrueForMaskGeoWhenMaskGeoTrueAndDeviceHasGeo() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final Device notMaskedDevice = Device.builder().geo(Geo.builder().build()).build();
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .maskGeo(true).build();

        // when
        privacyAnonymizationService.maskTcf(null, notMaskedDevice, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(true),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithFalseForMaskGeoWhenMaskGeoTrueAndDeviceAndUserAreNulls() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .maskGeo(true).build();

        // when
        privacyAnonymizationService.maskTcf(null, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(false),
                eq(false), eq(false));
    }

    @Test
    public void maskTcfShouldUpdateMetricsWithTrueForBlockAnalyticsWhenBlockAnalyticsFlagIsTrue() {
        // given
        given(bidderAliases.resolveBidder(eq(BIDDER_NAME))).willReturn(BIDDER_NAME);
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder()
                .blockAnalyticsReport(true).build();

        // when
        privacyAnonymizationService.maskTcf(null, null, BIDDER_NAME, bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(false),
                eq(true), eq(false));
    }

    @Test
    public void maskTcfShouldResolveBidderAliasesWhenIncrementingMetrics() {
        // given
        given(bidderAliases.resolveBidder(eq("bidderAlias"))).willReturn(BIDDER_NAME);
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.builder().build();

        // when
        privacyAnonymizationService.maskTcf(null, null, "bidderAlias", bidderAliases, MetricName.openrtb2web,
                privacyEnforcementAction);

        // then
        verify(metrics).updateAuctionTcfMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(false), eq(false),
                eq(false), eq(false));
    }

    private static Device notMaskedDevice() {
        return Device.builder()
                .ip("192.168.0.10")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334")
                .geo(Geo.builder().lon(-85.1245F).lat(189.9531F).country("US").build())
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
                .geo(geoTcfMasked())
                .build();
    }

    private static User userTcfMasked() {
        return User.builder()
                .id(null)
                .buyeruid(null)
                .geo(geoTcfMasked())
                .ext(maskExtUser())
                .build();
    }

    private static Geo geoTcfMasked() {
        return Geo.builder().lon(-85.12F).lat(189.95F).country("US").build();
    }

    private static ExtUser maskExtUser() {
        return ExtUser.builder().consent("consent").build();
    }
}
