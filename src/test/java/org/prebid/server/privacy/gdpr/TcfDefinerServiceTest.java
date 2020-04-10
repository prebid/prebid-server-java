package org.prebid.server.privacy.gdpr;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class TcfDefinerServiceTest {

    private static final String EEA_COUNTRY = "ua";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GdprService gdprService;
    @Mock
    private Tcf2Service tcf2Service;
    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private Metrics metrics;

    private TcfDefinerService target;

    private Purposes purposes;

    private Purpose purpose1;

    private GdprConfig gdprConfig;

    @Before
    public void setUp() {
        given(geoLocationService.lookup(anyString(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").country(EEA_COUNTRY).build()));

        initPurposes();
        initGdpr();

        target = new TcfDefinerService(gdprConfig, singletonList(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, metrics);
    }

    private void initPurposes() {
        purpose1 = new Purpose(EnforcePurpose.base, true, emptyList());
        purposes = Purposes.builder()
                .p1(purpose1)
                .build();
    }

    private void initGdpr() {
        gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(true)
                .purposes(purposes)
                .build();
    }

    @Test
    public void resultForShouldAllowAllWhenGdprIsDisabled() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(false).build();
        target = new TcfDefinerService(gdprConfig, singletonList(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, metrics);

        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), null, null, null, null, null);

        // then
        final TcfResponse expectedTcfResponse = TcfResponse.of(false,
                singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), null);
        assertThat(result.result()).isEqualTo(expectedTcfResponse);

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForShouldAllowAllWhenAccountEnforceGdprIsFalse() {
        // given
        final Account account = Account.builder()
                .enforceGdpr(false)
                .build();

        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), null, null, null, account, null);

        // then
        final TcfResponse expectedTcfResponse = TcfResponse.of(false,
                singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), null);
        assertThat(result.result()).isEqualTo(expectedTcfResponse);

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForShouldAllowAllWhenAccountGdprConfigGdprIsFalse() {
        // given
        final Account account = Account.builder()
                .gdpr(AccountGdprConfig.builder().enabled(false).build())
                .build();

        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), null, null, null, account, null);

        // then
        final TcfResponse expectedTcfResponse = TcfResponse.of(false,
                singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), null);
        assertThat(result.result()).isEqualTo(expectedTcfResponse);

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForShouldReturnGdprFromGeoLocationServiceWhenGdprFromRequestIsNotValid() {
        // when
        target.resultFor(singleton(1), emptySet(), null, "consent", "ip", null, null);

        // then
        verify(geoLocationService).lookup(eq("ip"), any());
        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void resultForShouldReturnRestrictAllWhenConsentIsNotValid() {
        // when
        target.resultFor(singleton(1), emptySet(), "1", "consent", "ip", null, null);

        // then
        verify(tcf2Service).permissionsFor(
                argThat(arg -> arg.getClass() == TcfDefinerService.TCStringEmpty.class), any(), any(), any());
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForShouldReturnAllowAllWhenGdprIsZero() {
        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), "0", "consent", "ip", null, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForShouldReturnAllowAllWhenGdprByGeoLookupIsZero() {
        // given
        given(geoLocationService.lookup(anyString(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("aa").country("aa").build()));

        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), null, "consent", "ip", null,
                null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), "aa"));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForShouldReturnAllowAllWhenGdprByGeoLookupIsFailedAndByDefaultIsZero() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(true).defaultValue("0").build();
        target = new TcfDefinerService(gdprConfig, singletonList(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, metrics);

        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("Bad ip"));

        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), null, "consent", "ip", null,
                null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForShouldReturnAllowAllWhenIpIsNullAndByDefaultIsZero() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(true).defaultValue("0").build();
        target = new TcfDefinerService(gdprConfig, singletonList(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, metrics);

        // when
        final Future<TcfResponse> result = target.resultFor(singleton(1), emptySet(), null, "consent", null, null,
                null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), emptyMap(), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForShouldReturnTcfResponseFromGdprServiceWhenConsentStringIsFirstVersion() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.allowAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(null, "b2", PrivacyEnforcementAction.allowAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);
        given(gdprService.resultFor(any(), any(), any(), any())).willReturn(Future.succeededFuture(vendorPermissions));

        // when
        final Set<String> bidderNames = new HashSet<>(Arrays.asList("b1", "b2"));
        final Future<TcfResponse> result = target.resultFor(singleton(1), bidderNames, "1",
                "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null, null);

        // then
        assertThat(result.succeeded()).isTrue();

        final HashMap<String, PrivacyEnforcementAction> expectedBidderNameToPrivacyMap = new HashMap<>();
        expectedBidderNameToPrivacyMap.put("b1", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b2", PrivacyEnforcementAction.allowAll());
        assertThat(result.result()).isEqualTo(TcfResponse.of(true, singletonMap(1, PrivacyEnforcementAction.allowAll()),
                expectedBidderNameToPrivacyMap, null));

        verifyZeroInteractions(tcf2Service);
    }

    @Test
    public void resultForShouldReturnTcfResponseFromTcf2ServiceWhenConsentStringIsSecondVersion() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.allowAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(null, "b2", PrivacyEnforcementAction.allowAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);
        given(tcf2Service.permissionsFor(any(), any(), any(), any())).willReturn(
                Future.succeededFuture(vendorPermissions));

        // when
        final Set<String> bidderNames = new HashSet<>(Arrays.asList("b1", "b2"));
        final Future<TcfResponse> result = target.resultFor(singleton(1), bidderNames, "1",
                "COwayg7OwaybYN6AAAENAPCgAIAAAAAAAAAAASkAAAAAAAAAAA", null, null, null);

        // then
        assertThat(result.succeeded()).isTrue();

        final HashMap<String, PrivacyEnforcementAction> expectedBidderNameToPrivacyMap = new HashMap<>();
        expectedBidderNameToPrivacyMap.put("b1", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b2", PrivacyEnforcementAction.allowAll());
        assertThat(result.result()).isEqualTo(TcfResponse.of(true, singletonMap(1, PrivacyEnforcementAction.allowAll()),
                expectedBidderNameToPrivacyMap, null));

        verifyZeroInteractions(gdprService);
    }
}
