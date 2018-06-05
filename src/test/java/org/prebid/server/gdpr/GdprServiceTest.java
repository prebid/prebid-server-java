package org.prebid.server.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.vendorlist.VendorList;
import org.prebid.server.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.gdpr.vendorlist.proto.VendorListInfo;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;

import java.util.Map;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

public class GdprServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private VendorList vendorList;

    private GdprService gdprService;

    @Before
    public void setUp() {
        given(vendorList.forVersion(anyInt(), any())).willReturn(Future.succeededFuture(givenVendorList()));

        gdprService = new GdprService(null, emptyList(), vendorList, "1");
    }

    @Test
    public void shouldFailIfGdprParamIsNeitherZeroNorOne() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), emptySet(), "invalid-gdpr", null, null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("The gdpr param must be either 0 or 1, given: invalid-gdpr");
    }

    @Test
    public void shouldReturnSuccessResultIfGdprParamIsZero() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "0", null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldFailIfGdprParamIsOneAndNoConsentParam() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), emptySet(), "1", null, null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("The gdpr_consent param is required when gdpr=1");
    }

    @Test
    public void shouldFailIfGdprParamIsOneButConsentParamIsInvalid() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), emptySet(), "1", "invalid-consent", null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("The gdpr_consent param 'invalid-consent' is malformed, "
                + "parsing error: requesting bit beyond bit string length");
    }

    @Test
    public void shouldReturnRestrictedResultIfPurposeIsNotAllowed() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(singleton(GdprPurpose.adSelectionAndDeliveryAndReporting), singleton(1), "1",
                        "BN5lERiOMYEdiAKAWXEND1HoSBE6CAFAApAMgBkIDIgM0AgOJxAnQA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, false);
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNull() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(null), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA",
                        null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(null, false);
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNotAllowed() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(9), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null,
                        null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(9, false);
    }

    @Test
    public void shouldReturnAllowedResultIfGdprParamIsOneAndConsentParamIsValid() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), "1",
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotFoundButDefaultGdprIsZero() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.failedFuture("country not found"));
        gdprService = new GdprService(geoLocationService, emptyList(), vendorList, "0");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, emptyList(), vendorList, "1");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndConsentParamIsValidAndCountryIsInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, singletonList("country1"), vendorList, "1");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), null,
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", "ip", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndNoIpButGdprDefaultValueIsZero() {
        // given
        gdprService = new GdprService(null, emptyList(), vendorList, "0");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    private static VendorListInfo givenVendorList() {
        return VendorListInfo.of(0, null,
                singletonList(Vendor.of(1, null, singletonList(GdprPurpose.informationStorageAndAccess.getId()))));
    }
}
