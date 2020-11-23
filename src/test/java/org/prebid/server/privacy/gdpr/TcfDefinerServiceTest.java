package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TCStringEmpty;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.model.Privacy;
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
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
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
    private IpAddressHelper ipAddressHelper;
    @Mock
    private Metrics metrics;

    private TcfDefinerService tcfDefinerService;

    @Before
    public void setUp() {
        given(geoLocationService.lookup(anyString(), any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").country(EEA_COUNTRY).build()));

        final GdprConfig gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(true)
                .purposes(Purposes.builder()
                        .p1(Purpose.of(EnforcePurpose.basic, true, emptyList()))
                        .build())
                .build();

        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);
    }

    @Test
    public void resolveTcfContextShouldReturnContextWhenGdprIsDisabled() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(false).build();
        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), null, null, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldReturnContextWithGdprZeroWhenGdprIsDisabledByAccountForRequestType() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(null, false, null, null))
                .build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), null, null, accountGdprConfig, MetricName.amp, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.builder().build());

        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldReturnContextWhenGdprIsDisabledByAccount() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder().enabled(false).build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), null, accountGdprConfig, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldCheckAccountConfigValueWhenRequestTypeIsUnknown() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabled(false)
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                .build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), null, null, accountGdprConfig, MetricName.legacy, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldCheckServiceConfigValueWhenRequestTypeIsUnknown() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(false).build();

        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                .build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), null, null, accountGdprConfig, MetricName.legacy, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyZeroInteractions(geoLocationService);
        verifyZeroInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldConsiderPresenceOfConsentStringAsInScope() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder()
                .enabled(true)
                .consentStringMeansInScope(true)
                .build();

        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        final String vendorConsent = "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA";

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, vendorConsent, null, null), null, null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                TcfContext::getGdpr,
                TcfContext::getConsentString,
                TcfContext::getGeoInfo,
                TcfContext::getInEea,
                TcfContext::getIpAddress)
                .containsExactly("1", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA", null, null, null);
        assertThat(result.result().getConsent()).isNotNull();

        verifyZeroInteractions(geoLocationService);
        verify(metrics).updatePrivacyTcfRequestsMetric(1);
        verify(metrics).updatePrivacyTcfGeoMetric(1, null);
    }

    @Test
    public void resolveTcfContextShouldReturnGdprFromCountryWhenGdprFromRequestIsNotValid() {

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(EMPTY, "consent", null, null), EEA_COUNTRY, "ip", null, null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                TcfContext::getGdpr,
                TcfContext::getConsentString,
                TcfContext::getGeoInfo,
                TcfContext::getInEea,
                TcfContext::getIpAddress)
                .containsExactly("1", "consent", null, true, "ip");

        verifyZeroInteractions(geoLocationService);
        verify(metrics).updatePrivacyTcfGeoMetric(2, true);
    }

    @Test
    public void resolveTcfContextShouldReturnGdprFromGeoLocationServiceWhenGdprFromRequestIsNotValid() {
        // given
        given(ipAddressHelper.maskIpv4(anyString())).willReturn("ip-masked");

        final GeoInfo geoInfo = GeoInfo.builder().vendor("vendor").country("ua").build();
        given(geoLocationService.lookup(eq("ip"), any())).willReturn(Future.succeededFuture(geoInfo));

        final String consentString = "COwayg7OwaybYN6AAAENAPCgAIAAAAAAAAAAASkAAAAAAAAAAA";

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(EMPTY, consentString, null, null), "ip", null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                TcfContext::getGdpr,
                TcfContext::getConsentString,
                TcfContext::getGeoInfo,
                TcfContext::getInEea,
                TcfContext::getIpAddress)
                .containsExactly("1", consentString, geoInfo, true, "ip-masked");

        verify(ipAddressHelper).maskIpv4(eq("ip"));
        verify(geoLocationService).lookup(eq("ip-masked"), any());
        verify(metrics).updateGeoLocationMetric(true);
        verify(metrics).updatePrivacyTcfGeoMetric(2, true);
    }

    @Test
    public void resolveTcfContextShouldConsultDefaultValueWhenGeoLookupFailed() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder()
                .enabled(true)
                .defaultValue("0")
                .build();
        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("Bad ip"));

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), "ip", null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                TcfContext::getGdpr,
                TcfContext::getConsentString,
                TcfContext::getGeoInfo,
                TcfContext::getInEea,
                TcfContext::getIpAddress)
                .containsExactly("0", null, null, null, "ip");

        verify(metrics).updateGeoLocationMetric(false);
    }

    @Test
    public void resolveTcfContextShouldConsultDefaultValueAndSkipGeoLookupWhenIpIsNull() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder()
                .enabled(true)
                .defaultValue("0")
                .build();
        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                gdprService,
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("Bad ip"));

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.of(null, null, null, null), null, null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                TcfContext::getGdpr,
                TcfContext::getConsentString,
                TcfContext::getGeoInfo,
                TcfContext::getInEea,
                TcfContext::getIpAddress)
                .containsExactly("0", null, null, null, null);

        verifyZeroInteractions(geoLocationService);
    }

    @Test
    public void resolveTcfContextShouldIncrementMissingConsentStringMetric() {
        // when
        tcfDefinerService.resolveTcfContext(
                Privacy.of("1", EMPTY, null, null), null, null, null, null);

        // then
        verify(metrics).updatePrivacyTcfMissingMetric();
    }

    @Test
    public void resolveTcfContextShouldIncrementInvalidConsentStringMetric() {
        // when
        tcfDefinerService.resolveTcfContext(
                Privacy.of("1", "abc", null, null), null, null, null, null);

        // then
        verify(metrics).updatePrivacyTcfInvalidMetric();
    }

    @Test
    public void resultForVendorIdsShouldNotSetTcfRequestsAndTcfGeoMetricsWhenConsentIsNotValid() {
        // given
        given(tcf2Service.permissionsFor(any(), any())).willReturn(Future.succeededFuture());

        // when
        tcfDefinerService.resultForVendorIds(singleton(1), TcfContext.builder()
                .gdpr("1")
                .consent(TCStringEmpty.create())
                .ipAddress("ip")
                .build());

        // then
        verify(metrics, never()).updatePrivacyTcfRequestsMetric(anyInt());
        verify(metrics, never()).updatePrivacyTcfGeoMetric(anyInt(), any());
    }

    @Test
    public void resultForVendorIdsShouldAllowAllWhenGdprIsZero() {
        // when
        final Future<TcfResponse<Integer>> result = tcfDefinerService.resultForVendorIds(
                singleton(1), TcfContext.builder().gdpr("0").build());

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyZeroInteractions(gdprService);
        verifyZeroInteractions(tcf2Service);
    }

    @Test
    public void resultForVendorIdsShouldReturnRestrictAllWhenConsentIsMissing() {
        // given
        given(tcf2Service.permissionsFor(any(), any())).willReturn(Future.succeededFuture());

        // when
        tcfDefinerService.resultForVendorIds(singleton(1), TcfContext.builder()
                .gdpr("1")
                .consent(TCStringEmpty.create())
                .build());

        // then
        verify(tcf2Service).permissionsFor(any(), argThat(arg -> arg.getClass() == TCStringEmpty.class));
        verifyZeroInteractions(gdprService);
    }

    @Test
    public void resultForBidderNamesShouldReturnAllowAllWhenGdprIsZero() {
        // when
        final Future<TcfResponse<String>> result =
                tcfDefinerService.resultForBidderNames(singleton("b1"), TcfContext.builder().gdpr("0").build(), null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b1", PrivacyEnforcementAction.allowAll()), null));

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

        final String consentString = "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA";

        // when
        final Future<TcfResponse<Integer>> result = tcfDefinerService.resultForVendorIds(
                new HashSet<>(asList(1, 2)),
                TcfContext.builder()
                        .gdpr("1")
                        .consentString(consentString)
                        .consent(TCString.decode(consentString))
                        .build());

        // then
        final HashMap<Integer, PrivacyEnforcementAction> expectedVendorIdToPrivacyMap = new HashMap<>();
        expectedVendorIdToPrivacyMap.put(1, PrivacyEnforcementAction.allowAll());
        expectedVendorIdToPrivacyMap.put(2, PrivacyEnforcementAction.restrictAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedVendorIdToPrivacyMap, null));

        verifyZeroInteractions(tcf2Service);
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

        final String consentString = "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA";

        // when
        final Future<TcfResponse<String>> result = tcfDefinerService.resultForBidderNames(
                new HashSet<>(asList("b1", "b2", "b3")),
                TcfContext.builder()
                        .gdpr("1")
                        .consentString(consentString)
                        .consent(TCString.decode(consentString))
                        .build(),
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
    }

    @Test
    public void resultForVendorIdsShouldReturnTcfResponseFromTcf2ServiceWhenConsentStringIsNull() {
        // given
        given(tcf2Service.permissionsFor(anySet(), any())).willReturn(Future.succeededFuture(asList(
                VendorPermission.of(1, null, PrivacyEnforcementAction.allowAll()),
                VendorPermission.of(2, null, PrivacyEnforcementAction.allowAll()))));

        // when
        final Future<TcfResponse<Integer>> result = tcfDefinerService.resultForVendorIds(
                new HashSet<>(asList(1, 2)),
                TcfContext.builder()
                        .gdpr("1")
                        .consent(TCStringEmpty.create())
                        .build());

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
        final String consentString = "COwayg7OwaybYN6AAAENAPCgAIAAAAAAAAAAASkAAAAAAAAAAA";
        final Future<TcfResponse<String>> result = tcfDefinerService.resultForBidderNames(
                bidderNames,
                TcfContext.builder()
                        .gdpr("1")
                        .consentString(consentString)
                        .consent(TCString.decode(consentString))
                        .build(),
                null);

        // then
        final HashMap<String, PrivacyEnforcementAction> expectedBidderNameToPrivacyMap = new HashMap<>();
        expectedBidderNameToPrivacyMap.put("b1", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b2", PrivacyEnforcementAction.allowAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedBidderNameToPrivacyMap, null));

        verifyZeroInteractions(gdprService);
    }

    @Test
    public void isConsentStringValidShouldReturnTrueWhenStringIsValid() {
        assertThat(TcfDefinerService.isConsentStringValid("BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA")).isTrue();
    }

    @Test
    public void isConsentStringValidShouldReturnFalseWhenStringIsNull() {
        assertThat(TcfDefinerService.isConsentStringValid(null)).isFalse();
    }

    @Test
    public void isConsentStringValidShouldReturnFalseWhenStringNotValid() {
        assertThat(TcfDefinerService.isConsentStringValid("invalid")).isFalse();
    }
}
