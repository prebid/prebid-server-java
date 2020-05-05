package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
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
    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock
    private Metrics metrics;

    private PrivacyEnforcementService privacyEnforcementService;

    @Mock
    private BidderAliases aliases;
    private Timeout timeout;

    @Before
    public void setUp() {
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, restrictDeviceAndUser()), null)));
        given(aliases.resolveBidder(anyString())).willAnswer(invocation -> invocation.getArgument(0));

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);

        privacyEnforcementService = new PrivacyEnforcementService(
                bidderCatalog, tcfDefinerService, metrics, jacksonMapper, false, false);
    }

    @Test
    public void shouldMaskForCoppaWhenDeviceLmtIsOneAndRegsCoppaIsOneAndDoesNotCallTcfServices() {
        // given
        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Regs regs = Regs.of(1, mapper.valueToTree(ExtRegs.of(1, null)));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expected = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(userCoppaMasked())
                .device(givenCoppaMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1)))
                .build();
        assertThat(result).isEqualTo(singletonList(expected));

        verifyZeroInteractions(tcfDefinerService);
    }

    @Test
    public void shouldMaskForCcpaWhenUsPolicyIsValidAndCoppaIsZeroAndDoesNotCallTcfServices() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(bidderCatalog, tcfDefinerService, metrics,
                jacksonMapper, false,
                true);
        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Regs regs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1, "1YYY")));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expected = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .user(userTcfMasked())
                .device(givenTcfMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1)))
                .build();
        assertThat(result).isEqualTo(singletonList(expected));

        verifyZeroInteractions(tcfDefinerService);
    }

    @Test
    public void shouldTolerateEmptyBidderToBidderPrivacyResultList() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList(),
                bidRequestBuilder -> bidRequestBuilder
                        .user(null)
                        .device(null)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, emptyMap(), null, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(tcfDefinerService);

        assertThat(result).isEqualTo(emptyList());
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsNullAndExtRegsGdprIsOneAndNotGdprEnforcedAndResultByVendorNoEnforcement() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null)));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(notMaskedUser())
                .device(notMaskedDevice())
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), eq("1"), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsZeroAndRegsCoppaIsZeroAndExtRegsGdprIsZeroAndTcfDefinerServiceAllowAll() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final Regs regs = Regs.of(0, mapper.valueToTree(ExtRegs.of(0, null)));
        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(0));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, null, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(notMaskedUser())
                .device(givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(0)))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), eq("0"), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskForTcfWhenTcfServiceAllowAllAndDeviceLmtIsOne() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.allowAll()), null)));

        final ExtUser extUser = ExtUser.builder().build();
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

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(userTcfMasked())
                .device(givenTcfMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1)))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskForTcfWhenTcfDefinerServiceRestrictDeviceAndUser() {
        // given
        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(userTcfMasked())
                .device(deviceTcfMasked())
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskUserIdsWhenTcfDefinerServiceRestrictUserIds() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setRemoveUserIds(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = notMaskedExtUser();
        final User user = notMaskedUser(extUser);
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(givenNotMaskedUser(userBuilder -> userBuilder.buyeruid(null)
                        .ext(mapper.valueToTree(extUserTcfMasked()))))
                .device(notMaskedDevice())
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskUserIdsWhenTcfDefinerServiceRestrictUserIdsAndReturnNullWhenAllValuesMasked() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setRemoveUserIds(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = ExtUser.builder()
                .eids(singletonList(ExtUserEid.of("Test", "id", emptyList(), null)))
                .digitrust(ExtUserDigiTrust.of("idDigit", 12, 23))
                .build();
        final User user = User.builder()
                .buyeruid(BUYER_UID)
                .ext(mapper.valueToTree(extUser))
                .build();

        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskGeoWhenTcfDefinerServiceRestrictGeo() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskGeo(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then
        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .user(givenNotMaskedUser(userBuilder -> userBuilder.geo(userTcfMasked().getGeo())))
                .device(givenNotMaskedDevice(deviceBuilder -> deviceBuilder.geo(deviceTcfMasked().getGeo())))
                .requestBidder(BIDDER_NAME)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskDeviceIpWhenTcfDefinerServiceRestrictDeviceIp() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskDeviceIp(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
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

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldMaskDeviceInfoWhenTcfDefinerServiceRestrictDeviceInfo() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.allowAll();
        privacyEnforcementAction.setMaskDeviceInfo(true);

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, privacyEnforcementAction), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
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

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
    }

    @Test
    public void shouldRerunEmptyResultWhenTcfDefinerServiceRestrictRequest() {
        // given
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(BIDDER_NAME, PrivacyEnforcementAction.restrictAll()), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(null));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases)
                .result();

        // then

        final BidderPrivacyResult expectedBidderPrivacy = BidderPrivacyResult.builder()
                .requestBidder(BIDDER_NAME)
                .blockedRequestByTcf(true)
                .blockedAnalyticsByTcf(true)
                .build();
        assertThat(result).containsOnly(expectedBidderPrivacy);

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
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
        final Regs regs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null)));
        final HashMap<String, Integer> bidderToId = new HashMap<>();
        bidderToId.put(requestBidder1Name, 1);
        bidderToId.put(requestBidder1Alias, 2);
        bidderToId.put(bidder2Alias, 3);
        bidderToId.put(requestBidder3Name, 4);
        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(bidderToId),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

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

        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, bidderNameToTcfEnforcement, null)));

        given(aliases.resolveBidder(eq(requestBidder1Alias))).willReturn(requestBidder1Name);
        given(aliases.resolveBidder(eq(bidder2Alias))).willReturn(bidder2Name);
        given(aliases.resolveAliasVendorId(eq(bidder2Alias))).willReturn(bidder2AliasVendorId);

        // when
        final List<String> bidders =
                asList(requestBidder1Name, requestBidder1Alias, bidder2Alias, requestBidder3Name);
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, null, bidders, aliases)
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
        verify(tcfDefinerService)
                .resultForBidderNames(eq(bidderNames), any(), eq("1"), any(), any(), isNull(), eq(timeout));
    }

    @Test
    public void shouldNotReturnUserIfMaskingAppliedAndUserBecameEmptyObject() {
        // given
        final User user = User.builder()
                .buyeruid("buyeruid")
                .build();
        final Regs regs = Regs.of(1, null);
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, null, singletonList(BIDDER_NAME), aliases)
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
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException(
                        "Error when retrieving allowed purpose ids in a reason of invalid consent string")));

        final ExtUser extUser = ExtUser.builder().build();
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

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final Future<List<BidderPrivacyResult>> firstFuture = privacyEnforcementService
                .mask(context, bidderToUser, extUser, singletonList(BIDDER_NAME), aliases);

        // then
        assertThat(firstFuture.failed()).isTrue();
        assertThat(firstFuture.cause().getMessage())
                .isEqualTo("Error when retrieving allowed purpose ids in a reason of invalid consent string");

        verify(tcfDefinerService)
                .resultForBidderNames(eq(singleton(BIDDER_NAME)), any(), isNull(), any(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(tcfDefinerService);
    }

    @Test
    public void shouldThrowPrebidExceptionWhenExtRegsCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.createObjectNode().put("gdpr", "invalid"))));

        final AuctionContext context = auctionContext(bidRequest);

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> privacyEnforcementService.mask(
                        context, emptyMap(), null, singletonList(BIDDER_NAME), aliases))
                .withMessageStartingWith("Error decoding bidRequest.regs.ext:");
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsFalse() {
        // given
        final Ccpa ccpa = Ccpa.of("1YYY");

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnFalseWhenEnforcedPropertyIsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(bidderCatalog, tcfDefinerService, metrics,
                jacksonMapper, false,
                true);
        final Ccpa ccpa = Ccpa.of("1YNY");

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnTrueWhenEnforcedPropertyIsTrueAndCcpaReturnsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(bidderCatalog, tcfDefinerService, metrics,
                jacksonMapper, false,
                true);
        final Ccpa ccpa = Ccpa.of("1YYY");

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa)).isTrue();
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
        given(tcfDefinerService.resultForBidderNames(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, vendorIdToTcfEnforcement, null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Regs regs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null)));
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
                        .device(device)
                        .regs(regs));

        final AuctionContext context = auctionContext(bidRequest);

        // when
        final List<BidderPrivacyResult> result = privacyEnforcementService
                .mask(context, bidderToUser, extUser, bidders, aliases)
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
        assertThat(result).hasSize(3).containsOnly(expectedBidder1Masked, expectedBidder2Masked, expectedBidder3Masked);

        final HashSet<String> bidderNames = new HashSet<>(asList(bidder1Name, bidder2Name, bidder3Name));
        verify(tcfDefinerService).resultForBidderNames(eq(bidderNames), any(), eq("1"), isNull(), isNull(),
                any(),
                eq(timeout));
    }

    @Test
    public void shouldIncrementCcpaAndAuctionTcfMetrics() {
        // given
        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap("someAlias", user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap("someAlias", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        final AuctionContext context = auctionContext(bidRequest);

        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap("someAlias", restrictDeviceAndUser()), null)));

        given(aliases.resolveBidder(eq("someAlias"))).willReturn(BIDDER_NAME);

        // when
        privacyEnforcementService.mask(context, bidderToUser, extUser, singletonList("someAlias"), aliases);

        // then
        verify(metrics).updatePrivacyCcpaMetrics(eq(false), eq(false));
        verify(metrics).updateAuctionTcfMetrics(
                eq(BIDDER_NAME), eq(MetricName.openrtb2web), eq(true), eq(true), eq(false), eq(false));
    }

    private AuctionContext auctionContext(BidRequest bidRequest) {
        return AuctionContext.builder()
                .account(Account.builder().build())
                .requestTypeMetric(MetricName.openrtb2web)
                .bidRequest(bidRequest)
                .timeout(timeout)
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
                .buyeruid(BUYER_UID)
                .geo(Geo.builder().lon(-85.1245F).lat(189.9531F).country("US").build())
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                .build();
    }

    private static User notMaskedUser(ExtUser extUser) {
        return User.builder()
                .buyeruid(BUYER_UID)
                .geo(Geo.builder().lon(-85.1245F).lat(189.9531F).country("US").build())
                .ext(mapper.valueToTree(extUser))
                .build();
    }

    private static ExtUser notMaskedExtUser() {
        return ExtUser.builder()
                .eids(singletonList(ExtUserEid.of("Test", "id", emptyList(), null)))
                .digitrust(ExtUserDigiTrust.of("idDigit", 12, 23))
                .prebid(ExtUserPrebid.of(emptyMap()))
                .build();
    }

    private static Device deviceCoppaMasked() {
        return Device.builder()
                .ip("192.168.0.0")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0:0")
                .geo(Geo.builder().country("US").build())
                .build();
    }

    private static User userCoppaMasked() {
        return User.builder()
                .geo(Geo.builder().country("US").build())
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                .build();
    }

    private static Device deviceTcfMasked() {
        return Device.builder()
                .ip("192.168.0.0")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:0")
                .geo(Geo.builder().lon(-85.34F).lat(189.34F).country("US").build())
                .build();
    }

    private static User userTcfMasked() {
        return User.builder()
                .buyeruid(null)
                .geo(Geo.builder().lon(-85.12F).lat(189.95F).country("US").build())
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                .build();
    }

    private static User userTcfMasked(ExtUser extUser) {
        return User.builder()
                .buyeruid(null)
                .geo(Geo.builder().lon(-85.12F).lat(189.95F).country("US").build())
                .ext(mapper.valueToTree(extUser))
                .build();
    }

    private static ExtUser extUserTcfMasked() {
        return ExtUser.builder()
                .prebid(ExtUserPrebid.of(emptyMap()))
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
        return singletonList(givenImp(ext, UnaryOperator.identity()));
    }

    private static <T> Imp givenImp(T ext, UnaryOperator<Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder().ext(mapper.valueToTree(ext))).build();
    }

    private static PrivacyEnforcementAction restrictDeviceAndUser() {
        return PrivacyEnforcementAction.builder()
                .maskDeviceInfo(true)
                .maskDeviceIp(true)
                .maskGeo(true)
                .removeUserIds(true)
                .build();
    }
}
