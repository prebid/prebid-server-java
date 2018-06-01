package org.prebid.server.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;

import java.util.Map;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class GdprServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GeoLocationService geoLocationService;

    private GdprService gdprService;

    @Before
    public void setUp() {
        gdprService = new GdprService(null, emptyList(), "1");
    }

    @Test
    public void shouldFailIfGdprParamIsNeitherZeroNorOne() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), emptySet(), "invalid-gdpr", null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("The gdpr param must be either 0 or 1, given: invalid-gdpr");
    }

    @Test
    public void shouldReturnSuccessResultIfGdprParamIsZero() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), "0", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldFailIfGdprParamIsOneAndNoConsentParam() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), emptySet(), "1", null, null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("The gdpr_consent param is required when gdpr=1");
    }

    @Test
    public void shouldFailIfGdprParamIsOneButConsentParamIsInvalid() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), emptySet(), "1", "invalid-consent", null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("The gdpr_consent param is invalid: invalid-consent");
    }

    @Test
    public void shouldReturnRestrictedResultIfPurposeIsNotAllowed() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(singleton(GdprPurpose.adSelectionAndDeliveryAndReporting), singleton(1), "1",
                        "BN5lERiOMYEdiAKAWXEND1HoSBE6CAFAApAMgBkIDIgM0AgOJxAnQA", null);

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
                        null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(null, false);
    }

    @Test
    public void shouldReturnRestrictedResultIfVendorIdIsNotAllowed() {
        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(9), "1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

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
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotFoundButDefaultGdprIsZero() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.failedFuture("country not found"));
        gdprService = new GdprService(geoLocationService, emptyList(), "0");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndCountryIsNotInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, emptyList(), "1");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndConsentParamIsValidAndCountryIsInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture(GeoInfo.of("country1")));
        gdprService = new GdprService(geoLocationService, singletonList("country1"), "1");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(singleton(GdprPurpose.informationStorageAndAccess), singleton(1), null,
                        "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }

    @Test
    public void shouldReturnAllowedResultIfNoGdprParamAndNoIpButGdprDefaultValueIsZero() {
        // given
        gdprService = new GdprService(null, emptyList(), "0");

        // when
        final Future<Map<Integer, Boolean>> future =
                gdprService.resultByVendor(emptySet(), singleton(1), null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(1, true);
    }
}
