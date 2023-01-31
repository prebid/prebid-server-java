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
import org.prebid.server.auction.model.IpAddress;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class TcfDefinerServiceTest {

    private static final String EEA_COUNTRY = "ua";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

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
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().build(), null, null, MetricName.setuid, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldReturnContextWithGdprZeroWhenGdprIsDisabledByAccountForRequestType() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(null, false, null, null))
                .build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().build(), null, null, accountGdprConfig, MetricName.amp, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldReturnContextWhenGdprIsDisabledByAccount() {
        // given
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder().enabled(false).build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().build(), null, accountGdprConfig, MetricName.setuid, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(metrics);
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
                Privacy.builder().build(), null, null, accountGdprConfig, MetricName.setuid, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldCheckServiceConfigValueWhenRequestTypeIsUnknown() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder().enabled(false).build();

        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
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
                Privacy.builder().build(), null, null, accountGdprConfig, MetricName.setuid, null, null);

        // then
        assertThat(result).succeededWith(TcfContext.empty());

        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(metrics);
    }

    @Test
    public void resolveTcfContextShouldConsiderTcfVersionOneAsCorruptedVersionTwo() {
        // given
        final GdprConfig gdprConfig = GdprConfig.builder()
                .enabled(true)
                .consentStringMeansInScope(true)
                .build();

        tcfDefinerService = new TcfDefinerService(
                gdprConfig,
                singleton(EEA_COUNTRY),
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        final String vendorConsent = "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA";

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().gdpr("1").consentString(vendorConsent).ccpa(null).coppa(null).build(), "london", null,
                null, MetricName.setuid, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result().getConsent()).isInstanceOf(TCStringEmpty.class);
        assertThat(result.result().getWarnings())
                .containsExactly("Parsing consent string:\"BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA\" failed. "
                        + "TCF version 1 is deprecated and treated as corrupted TCF version 2");
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
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        final String vendorConsent = "CPBCa-mPBCa-mAAAAAENA0CAAEAAAAAAACiQAaQAwAAgAgABoAAAAAA";

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().consentString(vendorConsent).build(), null, null, null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::isConsentValid,
                        TcfContext::getGeoInfo,
                        TcfContext::getInEea,
                        TcfContext::getIpAddress)
                .containsExactly(true, "CPBCa-mPBCa-mAAAAAENA0CAAEAAAAAAACiQAaQAwAAgAgABoAAAAAA",
                        true, null, null, null);
        assertThat(result.result().getConsent()).isNotNull();

        verifyNoInteractions(geoLocationService);
        verify(metrics).updatePrivacyTcfRequestsMetric(2);
        verify(metrics).updatePrivacyTcfGeoMetric(2, null);
    }

    @Test
    public void resolveTcfContextShouldReturnGdprFromCountryWhenGdprFromRequestIsNotValid() {
        // given
        final Privacy privacy = Privacy.builder().gdpr(EMPTY).consentString("consent").build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                privacy, EEA_COUNTRY, "ip", null, null, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::getGeoInfo,
                        TcfContext::getInEea,
                        TcfContext::getIpAddress)
                .containsExactly(true, "consent", null, true, "ip");

        verifyNoInteractions(geoLocationService);
        verify(metrics).updatePrivacyTcfGeoMetric(2, true);
    }

    @Test
    public void resolveTcfContextShouldReturnGdprFromGeoLocationServiceWhenGdprFromRequestIsNotValid() {
        // given
        given(ipAddressHelper.toIpAddress(anyString())).willReturn(IpAddress.of("ip", IpAddress.IP.v4));
        given(ipAddressHelper.maskIpv4(anyString())).willReturn("ip-masked");

        final GeoInfo geoInfo = GeoInfo.builder().vendor("vendor").country("ua").build();
        given(geoLocationService.lookup(eq("ip"), any())).willReturn(Future.succeededFuture(geoInfo));

        final String consentString = "COwayg7OwaybYN6AAAENAPCgAIAAAAAAAAAAASkAAAAAAAAAAA";
        final Privacy privacy = Privacy.builder().gdpr(EMPTY).consentString(consentString).build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                privacy, "ip", null, MetricName.setuid, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::getGeoInfo,
                        TcfContext::getInEea,
                        TcfContext::getIpAddress)
                .containsExactly(true, consentString, geoInfo, true, "ip-masked");

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
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("Bad ip"));

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().build(), "ip", null, MetricName.setuid, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::getGeoInfo,
                        TcfContext::getInEea,
                        TcfContext::getIpAddress)
                .containsExactly(false, null, null, null, "ip");

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
                tcf2Service,
                geoLocationService,
                bidderCatalog,
                ipAddressHelper,
                metrics);

        given(geoLocationService.lookup(anyString(), any())).willReturn(Future.failedFuture("Bad ip"));

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                Privacy.builder().build(), null, null, MetricName.setuid, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::getGeoInfo,
                        TcfContext::getInEea,
                        TcfContext::getIpAddress)
                .containsExactly(false, null, null, null, null);

        verifyNoInteractions(geoLocationService);
    }

    @Test
    public void resolveTcfContextShouldReturnTcfContextWithConsentValidAsTrue() {
        // given
        final Privacy privacy = Privacy.builder()
                .gdpr("1")
                .consentString("CPBCa-mPBCa-mAAAAAENA0CAAEAAAAAAACiQAaQAwAAgAgABoAAAAAA")
                .build();
        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                privacy, null, null, MetricName.setuid, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::isConsentValid)
                .containsExactly(true, "CPBCa-mPBCa-mAAAAAENA0CAAEAAAAAAACiQAaQAwAAgAgABoAAAAAA", true);
    }

    @Test
    public void resolveTcfContextShouldReturnTcfContextWithConsentValidAsFalse() {
        // given
        final Privacy privacy = Privacy.builder().gdpr("1").consentString("invalid").build();

        // when
        final Future<TcfContext> result = tcfDefinerService.resolveTcfContext(
                privacy, null, null, MetricName.setuid, null, null);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result()).extracting(
                        TcfContext::isInGdprScope,
                        TcfContext::getConsentString,
                        TcfContext::isConsentValid)
                .containsExactly(true, "invalid", false);
    }

    @Test
    public void resolveTcfContextShouldIncrementMissingConsentStringMetric() {
        // given
        final Privacy privacy = Privacy.builder().gdpr("1").consentString(EMPTY).build();

        // when
        tcfDefinerService.resolveTcfContext(
                privacy, null, null, MetricName.setuid, null, null);

        // then
        verify(metrics).updatePrivacyTcfMissingMetric();
    }

    @Test
    public void resolveTcfContextShouldIncrementInvalidConsentStringMetric() {
        final Privacy privacy = Privacy.builder().gdpr("1").consentString("abc").build();

        // when
        tcfDefinerService.resolveTcfContext(
                privacy, null, null, MetricName.setuid, null, null);

        // then
        verify(metrics).updatePrivacyTcfInvalidMetric();
    }

    @Test
    public void resultForVendorIdsShouldNotSetTcfRequestsAndTcfGeoMetricsWhenConsentIsNotValid() {
        // given
        given(tcf2Service.permissionsFor(any(), any())).willReturn(Future.succeededFuture());

        // when
        tcfDefinerService.resultForVendorIds(singleton(1), TcfContext.builder()
                .inGdprScope(true)
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
                singleton(1), TcfContext.builder().inGdprScope(false).build());

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap(1, PrivacyEnforcementAction.allowAll()), null));

        verifyNoInteractions(tcf2Service);
    }

    @Test
    public void resultForVendorIdsShouldReturnRestrictAllWhenConsentIsMissing() {
        // given
        given(tcf2Service.permissionsFor(any(), any())).willReturn(Future.succeededFuture());

        // when
        tcfDefinerService.resultForVendorIds(singleton(1), TcfContext.builder()
                .inGdprScope(true)
                .consent(TCStringEmpty.create())
                .build());

        // then
        verify(tcf2Service).permissionsFor(any(), argThat(arg -> arg.getClass() == TCStringEmpty.class));
    }

    @Test
    public void resultForBidderNamesShouldReturnAllowAllWhenGdprIsZero() {
        // when
        final Future<TcfResponse<String>> result = tcfDefinerService.resultForBidderNames(
                singleton("b1"),
                TcfContext.builder().inGdprScope(false).build(),
                null);

        // then
        assertThat(result).succeededWith(
                TcfResponse.of(false, singletonMap("b1", PrivacyEnforcementAction.allowAll()), null));

        verifyNoInteractions(tcf2Service);
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
                        .inGdprScope(true)
                        .consent(TCStringEmpty.create())
                        .build());

        // then
        final HashMap<Integer, PrivacyEnforcementAction> expectedVendorIdToPrivacyMap = new HashMap<>();
        expectedVendorIdToPrivacyMap.put(1, PrivacyEnforcementAction.allowAll());
        expectedVendorIdToPrivacyMap.put(2, PrivacyEnforcementAction.allowAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedVendorIdToPrivacyMap, null));
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
                        .inGdprScope(true)
                        .consentString(consentString)
                        .consent(TCString.decode(consentString))
                        .build(),
                null);

        // then
        final HashMap<String, PrivacyEnforcementAction> expectedBidderNameToPrivacyMap = new HashMap<>();
        expectedBidderNameToPrivacyMap.put("b1", PrivacyEnforcementAction.allowAll());
        expectedBidderNameToPrivacyMap.put("b2", PrivacyEnforcementAction.allowAll());
        assertThat(result).succeededWith(TcfResponse.of(true, expectedBidderNameToPrivacyMap, null));
    }

    @Test
    public void isConsentStringValidShouldReturnTrueWhenStringIsValid() {
        assertThat(TcfDefinerService.isConsentStringValid("CPBCa-mPBCa-mAAAAAENA0CAAEAAAAAAACiQAaQAwAAgAgABoAAAAAA"))
                .isTrue();
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
