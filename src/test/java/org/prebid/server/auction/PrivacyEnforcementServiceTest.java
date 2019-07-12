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
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.Metrics;
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
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
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

    private final static String BIDDER_NAME = "someBidder";
    private final static String BUYER_UID = "uidval";
    private final static String PUBLISHER_ID = "pubId";

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

        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(true));
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, false), null)));

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);

        privacyEnforcementService = new PrivacyEnforcementService(gdprService, bidderCatalog, metrics, false);
    }

    @Test
    public void shouldTolerateEmptyBidderToUserMap() {
        // given and when
        final BidRequest bidRequest  = givenBidRequest(emptyList(),
                bidRequestBuilder -> bidRequestBuilder
                        .user(null)
                        .device(null)
                        .regs(null));

        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(emptyMap(), null, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verify(gdprService).resultByVendor(eq(singleton(15)), isNull(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).isEqualTo(emptyMap());
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsNullAndExtRegsGdprIsOneAndGdprServiceRespondIsGdprEnforcedWithTrueAndResultByVendorWithoutEnforcement() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(true));
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, singletonMap(15, true), null)));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final ExtRegs extRegs = ExtRegs.of(1);
        final Regs regs = Regs.of(null, mapper.valueToTree(extRegs));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, extUser, extRegs, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then

        verify(gdprService).isGdprEnforced(eq("1"), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verify(gdprService).resultByVendor(eq(singleton(15)), eq("1"), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device, regs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldNotMaskWhenExtDeviceLmtIsNullAndGdprServiceRespondIsGdprEnforcedWithFalse() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(false));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, null, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device, null);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldNotMaskWhenExtRegsGdprIsOneDeviceLmtIsNullAndGdprServiceRespondIsGdprEnforcedWithFalse() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(false));

        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final ExtRegs extRegs = ExtRegs.of(1);
        final Regs regs = Regs.of(null, mapper.valueToTree(extRegs));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, null, extRegs, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(eq("1"), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device, regs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldNotMaskWhenDeviceLmtIsZeroAndRegsCoppaIsZeroAndExtRegsGdprIsZeroGdprServiceRespondIsGdprEnforcedWithFalse() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(false));

        final ExtRegs extRegs = ExtRegs.of(0);
        final Regs regs = Regs.of(0, mapper.valueToTree(extRegs));
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
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, null, extRegs, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(eq("0"), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(user, device, regs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldResolveBidderNameByAliases() {
        // given
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final ExtRegs extRegs = ExtRegs.of(1);
        final Regs regs = Regs.of(null, mapper.valueToTree(extRegs));
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
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, null, extRegs, singletonList(BIDDER_NAME), aliases, bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(bidderCatalog, times(2)).bidderInfoByName(BIDDER_NAME);
        verifyNoMoreInteractions(bidderCatalog);
        verify(gdprService).isGdprEnforced(eq("1"), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verify(gdprService).resultByVendor(eq(singleton(15)), eq("1"), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(BIDDER_NAME);
        verifyNoMoreInteractions(metrics);

        final Regs expectedRegs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1)));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), deviceGdprMasked(),
                expectedRegs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(alias, expected));
    }

    @Test
    public void shouldMaskedForCoppaWhenRegsCoppaIsOne() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(false));

        final Regs regs = Regs.of(1, null);
        final User user = notMaskedUser();
        final Device device = notMaskedDevice();
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, null, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verifyNoMoreInteractions(gdprService);

        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userCoppaMasked(), deviceCoppaMasked(),
                regs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldMaskedForGdprWhenGdprServiceRespondIsGdprEnforcedWithTrueAndResultByVendorWithEnforcementResponse() {
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
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, extUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verify(gdprService).resultByVendor(eq(singleton(15)), isNull(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        final Regs expectedRegs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1)));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), deviceGdprMasked(),
                expectedRegs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldMaskedForGdprWhenGdprServiceRespondIsGdprEnforcedWithFalseAndDeviceLmtIsOne() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(false));

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
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, extUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        final Regs expectedRegs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1)));
        final Device expectedDevice = givenGdprMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userGdprMasked(), expectedDevice,
                expectedRegs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @Test
    public void shouldMaskedForGdprAndCoppaWhenGdprServiceRespondIsGdprEnforcedWithFalseAndDeviceLmtIsOneAndRegsCoppaIsOne() {
        // given
        given(gdprService.isGdprEnforced(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(false));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Regs regs = Regs.of(1, null);
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, extUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        final Regs expectedRegs = Regs.of(1, mapper.valueToTree(ExtRegs.of(1)));
        //Coppa includes all masked fields for Gdpr
        final Device expectedDevice = givenCoppaMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userCoppaMasked(), expectedDevice,
                expectedRegs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnFailedFutureWhenGdprServiceIsReturnFailedFuture() {
        // given
        given(gdprService.resultByVendor(any(), any(), any(), any(), any()))
                .willReturn(
                        Future.failedFuture(new InvalidRequestException(
                                "Error when retrieving allowed purpose ids in a reason of invalid consent string")),
                        Future.failedFuture(new InvalidRequestException(
                                "Error when checking if vendor is allowed in a reason of invalid consent string")));

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final Regs regs = Regs.of(1, null);
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> firstFuture = privacyEnforcementService
                .mask(bidderToUser, extUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);
        final Future<Map<String, PrivacyEnforcementResult>> secondFuture = privacyEnforcementService
                .mask(bidderToUser, extUser, null, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService, times(2)).isGdprEnforced(isNull(), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verify(gdprService, times(2)).resultByVendor(eq(singleton(15)), isNull(), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verifyZeroInteractions(metrics);

        assertThat(firstFuture.failed()).isTrue();
        assertThat(firstFuture.cause().getMessage())
                .isEqualTo("Error when retrieving allowed purpose ids in a reason of invalid consent string");
        assertThat(secondFuture.failed()).isTrue();
        assertThat(secondFuture.cause().getMessage())
                .isEqualTo("Error when checking if vendor is allowed in a reason of invalid consent string");
    }

    @Test
    public void shouldMaskedForGdprAndCoppaWhenDeviceLmtIsOneAndRegsCoppaIsOneAndExtRegsGdprIsOneAndGdprServiceRespondIsGdprEnforcedWithTrueAndResultByVendorWithEnforcementResponse() {
        // given

        final ExtUser extUser = ExtUser.builder().build();
        final User user = notMaskedUser();
        final Device device = givenNotMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final ExtRegs extRegs = ExtRegs.of(1);
        final Regs regs = Regs.of(1, mapper.valueToTree(extRegs));
        final Map<String, User> bidderToUser = singletonMap(BIDDER_NAME, user);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(
                singletonMap(BIDDER_NAME, 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .user(user)
                        .device(device)
                        .regs(regs));

        // when
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, extUser, extRegs, singletonList(BIDDER_NAME), emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        verify(gdprService).isGdprEnforced(eq("1"), eq(PUBLISHER_ID), eq(singleton(15)), eq(timeout));
        verify(gdprService).resultByVendor(eq(singleton(15)), eq("1"), any(), any(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(eq(BIDDER_NAME));
        verifyNoMoreInteractions(metrics);

        final Regs expectedRegs = Regs.of(1, mapper.valueToTree(ExtRegs.of(1)));
        //Coppa includes all masked fields for Gdpr
        final Device expectedDevice = givenCoppaMaskedDevice(deviceBuilder -> deviceBuilder.lmt(1));
        final PrivacyEnforcementResult expected = PrivacyEnforcementResult.of(userCoppaMasked(), expectedDevice,
                expectedRegs);
        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(1)
                .containsOnly(entry(BIDDER_NAME, expected));
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
        final ExtRegs extRegs = ExtRegs.of(1);
        final Regs regs = Regs.of(0, mapper.valueToTree(extRegs));
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
        final Future<Map<String, PrivacyEnforcementResult>> masked = privacyEnforcementService
                .mask(bidderToUser, extUser, extRegs, bidders, emptyMap(), bidRequest, PUBLISHER_ID, timeout);

        // then
        final Regs expectedRegs = Regs.of(0, mapper.valueToTree(ExtRegs.of(1)));

        final PrivacyEnforcementResult expectedMasked = PrivacyEnforcementResult.of(
                userGdprMasked(), deviceGdprMasked(), expectedRegs);
        final PrivacyEnforcementResult expectedNotMasked = PrivacyEnforcementResult.of(user, device, regs);

        verify(gdprService).isGdprEnforced(eq("1"), eq(PUBLISHER_ID), anySet(), eq(timeout));
        verify(gdprService).resultByVendor(anySet(), eq("1"), isNull(), isNull(), eq(timeout));
        verifyNoMoreInteractions(gdprService);
        verify(metrics).updateGdprMaskedMetric(bidder1Name);
        verify(metrics).updateGdprMaskedMetric(bidder2Name);
        verifyNoMoreInteractions(metrics);

        assertThat(masked.succeeded()).isTrue();
        assertThat(masked.result()).hasSize(3)
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
                .ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:0")
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
            List<Imp> imp, Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
    }

    private static Device givenDevice(Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(Device.builder()).build();
    }

    private static Device givenNotMaskedDevice(Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(notMaskedDevice().toBuilder()).build();
    }

    private static Device givenGdprMaskedDevice(Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(deviceGdprMasked().toBuilder()).build();
    }

    private static Device givenCoppaMaskedDevice(Function<Device.DeviceBuilder, Device.DeviceBuilder> deviceBuilderCustomizer) {
        return deviceBuilderCustomizer.apply(deviceCoppaMasked().toBuilder()).build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
    }

    private static <T> Imp givenImp(T ext, Function<Imp.ImpBuilder, Imp.ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder().ext(mapper.valueToTree(ext))).build();
    }

    private static BidderInfo givenBidderInfo(int gdprVendorId, boolean enforceGdpr) {
        return new BidderInfo(true, null, null, null, new BidderInfo.GdprInfo(gdprVendorId, enforceGdpr));
    }
}

