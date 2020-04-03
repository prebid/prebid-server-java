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
import org.prebid.server.auction.model.PrivacyEnforcementResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.GdprService;
import org.prebid.server.privacy.gdpr.model.GdprResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.response.BidderInfo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
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
    private Metrics metrics;
    @Mock
    private GdprService gdprService;

    private Timeout timeout;
    private PrivacyEnforcementService privacyEnforcementService;

    @Before
    public void setUp() {
        given(bidderCatalog.bidderInfoByName(anyString())).willReturn(givenBidderInfo(15, true));

        given(gdprService.isGdprEnforced(any(), any(), any())).willReturn(true);
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, false), null)));

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);

        privacyEnforcementService = new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, jacksonMapper,
                false, false
        );
    }

    @Test
    public void shouldTolerateEmptyBidderToUserMap() {
        // given and when
        final BidRequest bidRequest = givenBidRequest(emptyList(),
                bidRequestBuilder -> bidRequestBuilder
                        .user(null)
                        .device(null)
                        .regs(null));

        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(emptyMap(), null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(true), eq(singleton(15)));
        verify(gdprService).resultByVendor(eq(singleton(15)), isNull(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        assertThat(result).isEqualTo(emptyMap());
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsNullAndExtRegsGdprIsOneAndNotGdprEnforcedAndResultByVendorNoEnforcement() {
        // given
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, true), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null)));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(eq("1"), eq(true), eq(singleton(15)));
        verify(gdprService).resultByVendor(eq(singleton(15)), eq("1"), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device);
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldNotMaskWhenExtDeviceLmtIsNullAndGdprServiceRespondIsGdprEnforcedWithFalse() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any())).willReturn(false);

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, false, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(false), eq(singleton(15)));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device);
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldNotMaskWhenExtRegsGdprIsOneDeviceLmtIsNullAndGdprServiceRespondIsGdprEnforcedWithFalse() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any())).willReturn(false);

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null)));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, false, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(eq("1"), eq(false), eq(singleton(15)));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device);
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsZeroAndRegsCoppaIsZeroAndExtRegsGdprIsZeroAndGdprEnforcedIsFalse() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any())).willReturn(false);

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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, false, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(eq("0"), eq(false), eq(singleton(15)));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device);
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldResolveBidderNameByAliases() {
        // given
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null)));
        final String alias = "alias";
        final Map<String, User> bidderToUser = singletonMap(alias, user);
        final Map<String, String> aliases = singletonMap(alias, BIDDER_NAME);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, null, singletonList(BIDDER_NAME), aliases, bidRequest, true, timeout)
                .result();

        // then
        verify(bidderCatalog, times(2)).bidderInfoByName(BIDDER_NAME);
        verifyNoMoreInteractions(bidderCatalog);
        verify(gdprService).isGdprEnforced(eq("1"), eq(true), eq(singleton(15)));
        verify(gdprService).resultByVendor(eq(singleton(15)), eq("1"), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(BIDDER_NAME);
        verifyNoMoreInteractions(metrics);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), deviceGdprMasked());
        assertThat(result).hasSize(1)
                .containsOnly(entry(alias, expected));
    }

    @Test
    public void shouldMaskForGdprWhenGdprEnforcedIsTrueAndResultByVendorWithEnforcementResponse() {
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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(true), eq(singleton(15)));
        verify(gdprService).resultByVendor(eq(singleton(15)), isNull(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), deviceGdprMasked());
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldMaskForGdprWhenGdprServiceRespondIsGdprEnforcedWithFalseAndDeviceLmtIsOne() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any())).willReturn(false);

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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, false, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(false), eq(singleton(15)));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        final Device expectedDevice = givenGdprMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), expectedDevice);
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldMaskForGdprAndCoppaWhenGdprEnforcedIsFalseAndDeviceLmtIsOne() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any())).willReturn(false);

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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, false, timeout)
                .result();

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(false), eq(singleton(15)));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        //Coppa includes all masked fields for Gdpr
        final Device expectedDevice = givenGdprMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), expectedDevice);
        assertThat(result).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        assertThat(result.values()).hasSize(1)
                .extracting(PrivacyEnforcementResult::getUser)
                .containsNull();
    }

    @Test
    public void shouldReturnFailedFutureWhenGdprServiceIsReturnFailedFuture() {
        // given
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
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

        // when
        final Future<Map<String, PrivacyEnforcementResult>> firstFuture = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(true), eq(singleton(15)));
        verify(gdprService).resultByVendor(eq(singleton(15)), isNull(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verifyZeroInteractions(metrics);

        assertThat(firstFuture.failed()).isTrue();
        assertThat(firstFuture.cause().getMessage())
                .isEqualTo("Error when retrieving allowed purpose ids in a reason of invalid consent string");
    }

    @Test
    public void shouldThrowPrebidExceptionWhenExtRegsCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, mapper.createObjectNode().put("gdpr", "invalid"))));

        // when and then
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> privacyEnforcementService.mask(emptyMap(), null, singletonList(BIDDER_NAME),
                        emptyMap(), bidRequest, true, timeout))
                .withMessageStartingWith("Error decoding bidRequest.regs.ext:");
    }

    @Test
    public void shouldMaskForCoppaWhenDeviceLmtIsOneAndRegsCoppaIsOneAndDoesNotCallGdprServices() {
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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        verifyZeroInteractions(gdprService);
        final Device expectedDevice = givenCoppaMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userCoppaMasked(), expectedDevice);
        assertThat(result).hasSize(1).containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldMaskForCcpaAndDoesNotCallGdprServicesWhenUsPolicyIsValidAndGdprIsEnforcedAndCOPPAIsZero() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, jacksonMapper,
                false, true);
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

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, singletonList(BIDDER_NAME), emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        verifyZeroInteractions(gdprService);
        final Device expectedDevice = givenGdprMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), expectedDevice);
        assertThat(result).hasSize(1).containsOnly(entry(BIDDER_NAME, expected));
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
        privacyEnforcementService = new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, jacksonMapper,
                false, true);
        final Ccpa ccpa = Ccpa.of("1YNY");

        // when and then
        assertThat(privacyEnforcementService.isCcpaEnforced(ccpa)).isFalse();
    }

    @Test
    public void isCcpaEnforcedShouldReturnTrueWhenEnforcedPropertyIsTrueAndCcpaReturnsTrue() {
        // given
        privacyEnforcementService = new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, jacksonMapper,
                false, true
        );
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
        given(bidderCatalog.bidderInfoByName(bidder1Name)).willReturn(givenBidderInfo(1, true));
        given(bidderCatalog.bidderInfoByName(bidder2Name)).willReturn(givenBidderInfo(2, true));
        given(bidderCatalog.bidderInfoByName(bidder3Name)).willReturn(givenBidderInfo(3, false));

        final HashMap<Integer, Boolean> vendorIdToGdprEnforce = new HashMap<>();
        vendorIdToGdprEnforce.put(1, false);
        vendorIdToGdprEnforce.put(2, false);
        vendorIdToGdprEnforce.put(3, true);
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, vendorIdToGdprEnforce, null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Regs regs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1, null)));
        final Map<String, User> bidderToUser = new HashMap<>();
        bidderToUser.put(bidder1Name, notMaskedUser());
        bidderToUser.put(bidder2Name, notMaskedUser());
        bidderToUser.put(bidder3Name, notMaskedUser());
        final List<String> bidders = Arrays.asList(bidder1Name, bidder2Name, bidder3Name);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Map<String, PrivacyEnforcementResult> result = privacyEnforcementService
                .mask(bidderToUser, extUser, bidders, emptyMap(), bidRequest, true, timeout)
                .result();

        // then
        final PrivacyEnforcementResult expectedMasked = PrivacyEnforcementResult.of(
                userGdprMasked(), deviceGdprMasked());
        final PrivacyEnforcementResult expectedNotMasked = PrivacyEnforcementResult.of(user, device);

        verify(gdprService).isGdprEnforced(eq("1"), eq(true), anySet());
        verify(gdprService).resultByVendor(anySet(), eq("1"), isNull(), isNull(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(bidder1Name);
        verify(metrics).updateGdprMaskedMetric(bidder2Name);
        verifyNoMoreInteractions(metrics);

        assertThat(result).hasSize(3)
                .contains(
                        entry(bidder1Name, expectedMasked),
                        entry(bidder2Name, expectedMasked),
                        entry(bidder3Name, expectedNotMasked));
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

    private static Device deviceGdprMasked() {
        return Device.builder()
                .ip("192.168.0.0")
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:0")
                .geo(Geo.builder().lon(-85.34F).lat(189.34F).country("US").build())
                .build();
    }

    private static User userGdprMasked() {
        return User.builder()
                .buyeruid(null)
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                .geo(Geo.builder().lon(-85.12F).lat(189.95F).country("US").build())
                .build();
    }

    private static BidRequest givenBidRequest(
            List<Imp> imp,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
    }

    private static Device givenNotMaskedDevice(
            Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(notMaskedDevice().toBuilder()).build();
    }

    private static Device givenGdprMaskedDevice(
            Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(deviceGdprMasked().toBuilder()).build();
    }

    private static Device givenCoppaMaskedDevice(
            Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(deviceCoppaMasked().toBuilder()).build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, Function.identity()));
    }

    private static <T> Imp givenImp(T ext, Function<Imp.ImpBuilder, Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder().ext(mapper.valueToTree(ext))).build();
    }

    private static BidderInfo givenBidderInfo(int gdprVendorId, boolean enforceGdpr) {
        return new BidderInfo(true, null, null, null,
                new BidderInfo.GdprInfo(gdprVendorId, enforceGdpr), false);
    }
}
