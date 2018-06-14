package org.prebid.server.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.gdpr.vendorlist.VendorListService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;

import java.util.Map;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class GdprServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private VendorListService vendorListService;

    private GdprService gdprService;

    @Before
    public void setUp() {
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(
                singletonMap(1, singleton(GdprPurpose.informationStorageAndAccess.getId()))));

        gdprService = new GdprService(null, emptyList(), vendorListService, "1");
    }

    @Test
    public void shouldReturnGdprFromGeoLocationServiceIfGdprFromRequestIsNotValid() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, singletonList("country1"), vendorListService, "1");

        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "15",
                        null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, false), "country1"));
    }

    @Test
    public void shouldReturnSuccessResultIfGdprParamIsZero() {
        // when
        final Future<?> future = gdprService.resultByVendor(emptySet(), singleton(1), "0", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnFalseForAllVendorIdsIfGdprParamIsOneAndNoConsentParam() {
        // when
        final Future<?> future = gdprService.resultByVendor(emptySet(), singleton(1), "1", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnTrueForAllVendorIdsIfGdprParamIsZeroAndNoConsentParam() {
        // when
        final Future<?> future = gdprService.resultByVendor(emptySet(), singleton(1), "0", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnFalseForAllVendorIdsIfGdprIsOneButConsentParamIsInvalid() {
        // when
        final Future<GdprResponse> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "1", "invalid-consent", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnTrueForAllVendorIdsIfGdprIsZeroButConsentParamIsInvalid() {
        // when
        final Future<GdprResponse> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "0", "invalid-consent", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfPurposeIsNotAllowed() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.adSelectionAndDeliveryAndReporting), singleton(1), "1",
                        "BN5lERiOMYEdiAKAWXEND1HoSBE6CAFAApAMgBkIDIgM0AgOJxAnQA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, false), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNull() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(null), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA",
                        null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(null, false), null));
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNotAllowed() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(9), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(9, false), null));
    }

    @Test
    public void shouldReturnAllowedResultIfGdprParamIsOneAndConsentParamIsValid() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotFoundButDefaultGdprIsZero() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.failedFuture("country not found"));
        gdprService = new GdprService(geoLocationService, emptyList(), vendorListService, "0");

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), null));
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, emptyList(), vendorListService, "1");

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), "country1"));
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndConsentParamIsValidAndCountryIsInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, singletonList("country1"), vendorListService, "1");

        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), null,
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), "country1"));
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndNoIpButGdprDefaultValueIsZero() {
        // given
        gdprService = new GdprService(null, emptyList(), vendorListService, "0");

        // when
        final Future<?> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GdprResponse.of(singletonMap(1, true), null));
    }

    @Test
    public void shouldNotCallGeoLocationServiceIfValidGdprAndIpAddressAreInRequest() {
        // when
        final Future<?> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        verifyZeroInteractions(geoLocationService);
    }
}
