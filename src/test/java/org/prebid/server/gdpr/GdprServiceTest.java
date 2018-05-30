package org.prebid.server.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.gdpr.model.GdprResult;
import org.prebid.server.geolocation.GeoLocationService;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
        gdprService = new GdprService(geoLocationService, emptyList(), "1", null);
    }

    @Test
    public void shouldReturnErrorResultIfGdprParamIsNeitherZeroNorOne() {
        // when
        final Future<GdprResponse> future = gdprService.analyze("invalid-gdpr", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.error_invalid_gdpr);
    }

    @Test
    public void shouldReturnSuccessResultIfGdprParamIsZero() {
        // when
        final Future<GdprResponse> future = gdprService.analyze("0", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.allowed);
    }

    @Test
    public void shouldReturnErrorResultIfGdprParamIsOneAndNoConsentParam() {
        // when
        final Future<GdprResponse> future = gdprService.analyze("1", null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.error_missing_consent);
    }

    @Test
    public void shouldReturnErrorResultIfGdprParamIsOneAndInvalidConsentParam() {
        // when
        final Future<GdprResponse> future = gdprService.analyze("1", "invalid-consent", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.error_invalid_consent);
    }

    @Test
    public void shouldReturnErrorResultIfVendorIdIsNull() {
        // given
        gdprService = new GdprService(null, emptyList(), "1", null);

        // when
        final Future<GdprResponse> future = gdprService.analyze("1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.restricted);
    }

    @Test
    public void shouldReturnErrorResultIfPurposeOneIsNotAllowed() {
        // given
        gdprService = new GdprService(null, emptyList(), "1", null);

        // when
        final Future<GdprResponse> future = gdprService.analyze("1",
                "BN5lERiOMYEdiAKAWXEND1HoSBE6CAFAApAMgBkIDIgM0AgOJxAnQA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.restricted);
    }

    @Test
    public void shouldReturnErrorResultIfVendorIdIsNotAllowed() {
        // given
        gdprService = new GdprService(null, emptyList(), "1", 9);

        // when
        final Future<GdprResponse> future = gdprService.analyze("1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.restricted);
    }

    @Test
    public void shouldReturnSuccessResultIfGdprParamIsOneAndConsentParamIsValid() {
        // given
        gdprService = new GdprService(null, emptyList(), "1", 1);

        // when
        final Future<GdprResponse> future = gdprService.analyze("1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.allowed);
    }

    @Test
    public void shouldReturnSuccessResultIfNoGdprParamAndCountryIsNotInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture("country1"));
        gdprService = new GdprService(geoLocationService, emptyList(), "1", 1);

        // when
        final Future<GdprResponse> future = gdprService.analyze(null, null, "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.allowed);
    }

    @Test
    public void shouldReturnSuccessResultIfNoGdprParamAndConsentParamIsValidAndCountryIsInEEA() {
        // given
        given(geoLocationService.lookup(anyString())).willReturn(Future.succeededFuture("country1"));
        gdprService = new GdprService(geoLocationService, singletonList("country1"), "1", 1);

        // when
        final Future<GdprResponse> future = gdprService.analyze(null, "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", "ip");

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.allowed);
    }

    @Test
    public void shouldReturnSuccessResultIfNoGdprParamAndNoIpAndGdprDefaultValueIsZero() {
        // given
        gdprService = new GdprService(null, emptyList(), "0", null);

        // when
        final Future<GdprResponse> future = gdprService.analyze(null, null, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getGdprResult()).isEqualTo(GdprResult.allowed);
    }
}
