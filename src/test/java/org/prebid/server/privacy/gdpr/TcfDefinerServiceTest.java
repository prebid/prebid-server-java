package org.prebid.server.privacy.gdpr;

import io.vertx.core.Future;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TCStringEmpty;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.EnabledForRequestType;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

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
    private BidderCatalog bidderCatalog;
    @Mock
    private Metrics metrics;

    private TcfDefinerService target;

    private Purposes purposes;

    private GdprConfig gdprConfig;

    @Before
    public void setUp() {
        given(geoLocationService.lookup(anyString(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").country(EEA_COUNTRY).build()));

        initPurposes();
        initGdpr();

        target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, bidderCatalog, metrics);
    }

    private void initPurposes() {
        final Purpose purpose1 = Purpose.of(EnforcePurpose.basic, true, emptyList());
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
    public void resultForVendorIdsShouldAllowAllWhenGdprIsDisabled() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(false).build();
        target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, bidderCatalog, metrics);

        // when
        final Future<TcfResponse<Integer>> result = target.resultForVendorIds(singleton(1), null, null, null, null,
                null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForBidderNamesShouldAllowAllWhenGdprIsDisabledByAccountForRequestType() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(null, false, null, null))
                .build();

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                singleton("b"), null, null, null, null, accountGdprConfig, MetricName.amp, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForBidderNamesShouldAllowAllWhenGdprIsDisabledByAccount() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder().enabled(false).build();

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                singleton("b"), null, null, null, null, accountGdprConfig, null, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForBidderNamesShouldCheckAccountEnabledWhenPerRequestConfigNotDefined() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabled(false)
                .build();

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                singleton("b"), null, null, null, null, accountGdprConfig, MetricName.amp, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForBidderNamesShouldCheckPbsEnabledWhenAccountLevelAndPerRequestLevelNotDefined() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(false)
                .purposes(purposes)
                .build();
        final TcfDefinerService target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService,
                tcf2Service, geoLocationService, bidderCatalog, metrics);

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .build();

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                singleton("b"), null, null, null, null, accountGdprConfig, MetricName.amp, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForBidderNamesShouldCheckAccountLevelEnabledConfigWhenRequestTypeIsUnknown() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabled(false)
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                .build();

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                singleton("b"), null, null, null, null, accountGdprConfig, MetricName.legacy, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForBidderNamesShouldCheckPBSLevelEnabledConfigWhenAccountLevelConfigIsNotDefined() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(false)
                .purposes(purposes)
                .build();

        final TcfDefinerService target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService,
                tcf2Service, geoLocationService, bidderCatalog, metrics);

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                .build();

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                singleton("b"), null, null, null, null, accountGdprConfig, MetricName.legacy, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForVendorIdsShouldAllowAllWhenGdprIsDisabledByAccount() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder().enabled(false).build();

        // when
        final Future<TcfResponse<Integer>> result = target.resultForVendorIds(singleton(1), "1", "consent", "ip",
                accountGdprConfig, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resultForVendorIdsShouldConsiderPresenceOfConsentStringAsInScope() {
        // given
        gdprConfig.setConsentStringMeansInScope(true);

        target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, bidderCatalog, metrics);

        // when
        final String vendorConsent = "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA";
        target.resultForVendorIds(singleton(1), null, vendorConsent, "ip", null, null);

        // then
        verify(gdprService).resultFor(any(), eq(vendorConsent));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
    }

    @Test
    public void resultForBidderNamesShouldConsiderPresenceOfConsentStringAsInScope() {
        // given
        gdprConfig.setConsentStringMeansInScope(true);

        target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, bidderCatalog, metrics);

        // when
        final String vendorConsent = "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA";
        target.resultForBidderNames(singleton("b"), null, vendorConsent, "ip", null, null);

        // then
        verify(gdprService).resultFor(any(), eq(vendorConsent));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(geoLocationService);
    }

    @Test
    public void resultForVendorIdsShouldReturnGdprFromGeoLocationServiceWhenGdprFromRequestIsNotValid() {
        // when
        target.resultForVendorIds(singleton(1), null, "consent", "ip", null, null);

        // then
        verify(geoLocationService).lookup(eq("ip"), any());
        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void resultForVendorIdsShouldReturnRestrictAllWhenConsentIsMissing() {
        // when
        target.resultForVendorIds(singleton(1), "1", null, "ip", null, null);

        // then
        verify(tcf2Service).permissionsFor(any(), argThat(arg -> arg.getClass() == TCStringEmpty.class));
        verifyZeroInteractions(gdprService);
        verify(metrics).updatePrivacyTcfMissingMetric();
    }

    @Test
    public void resultForVendorIdsShouldReturnRestrictAllWhenConsentIsNotValid() {
        // when
        target.resultForVendorIds(singleton(1), "1", "consent", "ip", null, null);

        // then
        verify(tcf2Service).permissionsFor(any(), argThat(arg -> arg.getClass() == TCStringEmpty.class));
        verifyZeroInteractions(gdprService);
        verify(metrics).updatePrivacyTcfInvalidMetric();
    }

    @Test
    public void resultForVendorIdsShouldNotSetTcfRequestsAndTcfGeoMetricsWhenConsentIsNotValid() {
        // when
        target.resultForVendorIds(singleton(1), "1", "consent", "ip", null, null);

        // then
        verify(metrics, never()).updatePrivacyTcfRequestsMetric(anyInt());
        verify(metrics, never()).updatePrivacyTcfGeoMetric(anyInt(), any());
    }

    @Test
    public void resultForVendorIdsShouldReturnAllowAllWhenGdprIsZero() {
        // when
        final Future<TcfResponse<Integer>> result =
                target.resultForVendorIds(singleton(1), "0", "consent", "ip", null, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForBidderNamesShouldReturnAllowAllWhenGdprIsZero() {
        // when
        final Future<TcfResponse<String>> result =
                target.resultForBidderNames(singleton("b1"), "0", "consent", "ip", null, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b1", PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForVendorIdsShouldReturnAllowAllWhenGdprByGeoLookupIsZero() {
        // given
        given(geoLocationService.lookup(anyString(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("aa").country("aa").build()));

        // when
        final Future<TcfResponse<Integer>> result =
                target.resultForVendorIds(singleton(1), null, "consent", "ip", null, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), "aa"));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForVendorIdsShouldReturnAllowAllWhenGdprByGeoLookupIsFailedAndByDefaultIsZero() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(true).defaultValue("0").build();
        target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, bidderCatalog, metrics);

        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("Bad ip"));

        // when
        final Future<TcfResponse<Integer>> result =
                target.resultForVendorIds(singleton(1), null, "consent", "ip", null, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForVendorIdsShouldReturnAllowAllWhenIpIsNullAndByDefaultIsZero() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(true).defaultValue("0").build();
        target = new TcfDefinerService(gdprConfig, singleton(EEA_COUNTRY), gdprService, tcf2Service,
                geoLocationService, bidderCatalog, metrics);

        // when
        final Future<TcfResponse<Integer>> result =
                target.resultForVendorIds(singleton(1), null, "consent", null, null, null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(tcf2Service);
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForVendorIdsShouldReturnTcfResponseFromGdprServiceWhenConsentStringIsFirstVersion() {
        // given
        given(gdprService.resultFor(anySet(), anyString()))
                .willReturn(Future.succeededFuture(asList(
                        VendorPermission.of(1, null, PrivacyEnforcementAction.allowAll()),
                        VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll()))));

        // when
        final Future<TcfResponse<Integer>> result = target.resultForVendorIds(
                new HashSet<>(asList(1, 2)),
                "1",
                "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA",
                null,
                AccountGdprConfig.builder().build(),
                null);

        // then
        final HashMap<Integer, PrivacyEnforcementAction> expectedVendorIdToPrivacyMap = new HashMap<>();
        expectedVendorIdToPrivacyMap.put(1, PrivacyEnforcementAction.allowAll());
        expectedVendorIdToPrivacyMap.put(2, PrivacyEnforcementAction.restrictAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedVendorIdToPrivacyMap, null));

        verifyZeroInteractions(tcf2Service);
        verify(metrics).updatePrivacyTcfRequestsMetric(1);
        verify(metrics).updatePrivacyTcfGeoMetric(1, null);
    }

    @Test
    public void resultForBidderNamesShouldReturnTcfResponseFromGdprServiceWhenConsentStringIsFirstVersion() {
        // given
        given(gdprService.resultFor(anySet(), anyString()))
                .willReturn(Future.succeededFuture(asList(
                        VendorPermission.of(1, null, PrivacyEnforcementAction.allowAll()),
                        VendorPermission.of(2, null, PrivacyEnforcementAction.allowAll()))));

        given(bidderCatalog.isActive(eq("b1"))).willReturn(true);
        given(bidderCatalog.isActive(eq("b2"))).willReturn(true);
        given(bidderCatalog.isActive(eq("b3"))).willReturn(false);
        given(bidderCatalog.vendorIdByName(eq("b1"))).willReturn(1);
        given(bidderCatalog.vendorIdByName(eq("b2"))).willReturn(2);

        // when
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                new HashSet<>(asList("b1", "b2", "b3")),
                "1",
                "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA",
                null,
                null,
                null);

        // then
        final HashMap<String, PrivacyEnforcementAction> expectedBidderNameToPrivacyMap = new HashMap<>();
        expectedBidderNameToPrivacyMap.put("b1", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b2", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b3", PrivacyEnforcementAction.builder()
                .removeUserIds(true)
                .maskGeo(true)
                .maskDeviceIp(true)
                .maskDeviceInfo(true)
                .blockAnalyticsReport(false)
                .blockBidderRequest(false)
                .blockPixelSync(true)
                .build());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedBidderNameToPrivacyMap, null));

        verifyZeroInteractions(tcf2Service);
        verify(metrics).updatePrivacyTcfRequestsMetric(1);
        verify(metrics).updatePrivacyTcfGeoMetric(1, null);
    }

    @Test
    public void resultForVendorIdsShouldReturnTcfResponseFromTcf2ServiceWhenConsentStringIsNull() {
        // given
        given(tcf2Service.permissionsFor(anySet(), any())).willReturn(Future.succeededFuture(asList(
                VendorPermission.of(1, null, PrivacyEnforcementAction.allowAll()),
                VendorPermission.of(2, null, PrivacyEnforcementAction.allowAll()))));

        // when
        final Future<TcfResponse<Integer>> result =
                target.resultForVendorIds(new HashSet<>(asList(1, 2)), "1", null, null, null, null);

        // then
        final HashMap<Integer, PrivacyEnforcementAction> expectedVendorIdToPrivacyMap = new HashMap<>();
        expectedVendorIdToPrivacyMap.put(1, PrivacyEnforcementAction.allowAll());
        expectedVendorIdToPrivacyMap.put(2, PrivacyEnforcementAction.allowAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedVendorIdToPrivacyMap, null));

        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForBidderNamesShouldReturnTcfResponseFromTcf2ServiceWhenConsentStringIsSecondVersion() {
        // given
        given(tcf2Service.permissionsFor(anySet(), any(), any(), any())).willReturn(Future.succeededFuture(asList(
                VendorPermission.of(1, "b1", PrivacyEnforcementAction.allowAll()),
                VendorPermission.of(null, "b2", PrivacyEnforcementAction.allowAll()))));

        // when
        final Set<String> bidderNames = new HashSet<>(asList("b1", "b2"));
        final Future<TcfResponse<String>> result = target.resultForBidderNames(
                bidderNames, "1", "COwayg7OwaybYN6AAAENAPCgAIAAAAAAAAAAASkAAAAAAAAAAA", null, null, null);

        // then
        final HashMap<String, PrivacyEnforcementAction> expectedBidderNameToPrivacyMap = new HashMap<>();
        expectedBidderNameToPrivacyMap.put("b1", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b2", PrivacyEnforcementAction.allowAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedBidderNameToPrivacyMap, null));

        verifyZeroInteractions(gdprService);
        verify(metrics).updatePrivacyTcfRequestsMetric(2);
        verify(metrics).updatePrivacyTcfGeoMetric(2, null);
    }

    @Test
    public void isGdprConsentValidShouldReturnTrueWhenStringIsValid() {
        // when
        final boolean result = TcfDefinerService.isGdprConsentValid("BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA");

        // then
        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void isGdprConsentValidShouldReturnFalseWhenStringIsNull() {
        // when
        final boolean result = TcfDefinerService.isGdprConsentValid(null);

        // then
        Assertions.assertThat(result).isFalse();
    }

    @Test
    public void isGdprConsentValidShouldReturnFalseWhenStringNotValid() {
        // when
        final boolean result = TcfDefinerService.isGdprConsentValid("invalid");

        // then
        Assertions.assertThat(result).isFalse();
    }
}
