package org.prebid.server.privacy.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.GdprInfoWithCountry;
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.GdprResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class GdprServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private Metrics metrics;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private VendorListService vendorListService;

    private GdprService gdprService;

    @Before
    public void setUp() {
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, singleton(GdprPurpose.informationStorageAndAccess.getId()))));
        given(geoLocationService.lookup(anyString(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").country("country1").build()));

        gdprService = new GdprService(emptyList(), "1", null, metrics, bidderCatalog, vendorListService);
    }

    @Test
    public void isGdprEnforcedShouldConsiderRequestValue() {
        // when
        final boolean result = gdprService.isGdprEnforced("1", null, emptySet());

        // then
        assertTrue(result);
    }

    @Test
    public void isGdprEnforcedShouldConsiderAccountConfigValue() {
        // when
        final boolean result = gdprService.isGdprEnforced(null, true, emptySet());

        // then
        assertTrue(result);
    }

    @Test
    public void isGdprEnforcedShouldConsiderGdprEnforcedVendorsIfIsGdprEnforcedIsNull() {
        // when
        final boolean result = gdprService.isGdprEnforced(null, null, singleton(1));

        // then
        assertTrue(result);
    }

    @Test
    public void shouldReturnGdprFromGeoLocationServiceIfGdprFromRequestIsNotValidAndUpdateMetrics() {
        // given
        gdprService = new GdprService(
                singletonList("country1"), "1", geoLocationService, metrics, bidderCatalog, vendorListService);

        // when
        final Future<?> future = gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess),
                singleton(1), "15", null, "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, false), "country1"));

        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void shouldReturnSuccessResultIfGdprParamIsZero() {
        // when
        final Future<?> future = gdprService.resultByVendor(emptySet(), singleton(1), "0", null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(false, singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnFalseForAllVendorIdsIfGdprParamIsOneAndNoConsentParam() {
        // when
        final Future<?> future = gdprService.resultByVendor(emptySet(), singleton(1), "1", null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnTrueForAllVendorIdsIfGdprParamIsZeroAndNoConsentParam() {
        // when
        final Future<?> future = gdprService.resultByVendor(emptySet(), singleton(1), "0", null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(false, singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnFalseForAllVendorIdsIfGdprIsOneButConsentParamIsInvalid() {
        // when
        final Future<GdprResponse> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "1", "invalid-consent", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnTrueForAllVendorIdsIfGdprIsZeroButConsentParamIsInvalid() {
        // when
        final Future<GdprResponse> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "0", "invalid-consent", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(false, singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfPurposeIsNotAllowed() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.adSelectionAndDeliveryAndReporting), singleton(1), "1",
                        "BN5lERiOMYEdiAKAWXEND1HoSBE6CAFAApAMgBkIDIgM0AgOJxAnQA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNull() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(null), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA",
                        null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(null, false), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNotAllowed() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(9), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null,
                        null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(9, false), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsAbsentInVendorConsent() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(20), "1", "BOb3F3yOb3F3yABABBENABoAAAABQAAAgA", null,
                        null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(20, false), null));
    }

    @Test
    public void shouldReturnAllowedResultIfGdprParamIsOneAndConsentParamIsValid() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotFoundButDefaultGdprIsZeroAndUpdateMetrics() {
        // given
        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("country not found"));
        gdprService = new GdprService(emptyList(), "0", geoLocationService, metrics, bidderCatalog, vendorListService);

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(false, singletonMap(1, true), null));

        verify(metrics).updateGeoLocationMetric(false);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotInEEA() {
        // given
        gdprService = new GdprService(emptyList(), "1", geoLocationService, metrics, bidderCatalog, vendorListService);

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(false, singletonMap(1, true), "country1"));

        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoPurposesProvided() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, new HashSet<>(Arrays.asList(1, 2, 3)))));

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "1",
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndConsentParamIsValidAndCountryIsInEEA() {
        // given
        gdprService = new GdprService(
                singletonList("country1"), "1", geoLocationService, metrics, bidderCatalog, vendorListService);

        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), null,
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, true), "country1"));

        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndNoIpButGdprDefaultValueIsZero() {
        // given
        gdprService = new GdprService(emptyList(), "0", null, metrics, bidderCatalog, vendorListService);

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(false, singletonMap(1, true), null));
    }

    @Test
    public void shouldNotCallGeoLocationServiceIfValidGdprAndIpAddressAreInRequest() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        null, "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        verifyZeroInteractions(geoLocationService);
    }

    @Test
    public void shouldReturnFailedFutureIfGdprSdkCantGetAllowedPurposesInAReasonOfInvalidConsentString() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        "BONciguONcjGKADACHENAOLS1r", null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage())
                .isEqualTo("Error when retrieving allowed purpose ids in a reason of invalid consent string");
    }

    @Test
    public void shouldReturnFailedFutureIfGdprSdkCantCheckIfVendorAllowedInAReasonOfInvalidConsentString() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        "BOSbaBZOSbaBoABABBENBcoAAAAgSABgBAA", null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage())
                .isEqualTo("Error when checking if vendor is allowed in a reason of invalid consent string");
    }

    @Test
    public void shouldReturnRestrictedResultIfGdprParamIsOneAndConsentHasNotAllVendorPurposes() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, new HashSet<>(Arrays.asList(1, 2, 3, 4)))));

        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(1), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnAllowedResultIfGdprParamIsOneAndConsentHasAllVendorPurposes() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, new HashSet<>(Arrays.asList(1, 2, 3)))));

        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(1), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(true, singletonMap(1, true), null));
    }

    @Test
    public void resultForShouldReturnVendorPermissionsWhenBidderNamesAndVendorIdsIsProvided() {
        // given
        final GdprInfoWithCountry<String> gdprInfo = GdprInfoWithCountry.of("1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA",
                null);

        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.vendorIdByName(anyString())).willReturn(2);

        final Map<Integer, Set<Integer>> vendorPermission = new HashMap<>();
        vendorPermission.put(1, new HashSet<>(Arrays.asList(2, 3, 4)));
        vendorPermission.put(2, new HashSet<>(Arrays.asList(1, 2)));

        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(vendorPermission));

        // when
        final Future<Collection<VendorPermission>> result =
                gdprService.resultFor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), singleton("b1"),
                        gdprInfo);

        // then
        assertThat(result.succeeded()).isTrue();

        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final PrivacyEnforcementAction expectedChangedAction = PrivacyEnforcementAction.restrictAll();
        expectedChangedAction.setBlockPixelSync(false);
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", expectedChangedAction);
        assertThat(result.result()).usingFieldByFieldElementComparator().containsOnly(vendorPermission1,
                vendorPermission2);
    }
}
