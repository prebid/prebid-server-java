package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.Timeout;
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
import org.prebid.server.settings.model.GdprConfig;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TcfDefinerService {

    private static final Logger logger = LoggerFactory.getLogger(TcfDefinerService.class);

    private static final String GDPR_ZERO = "0";
    private static final String GDPR_ONE = "1";

    private final boolean gdprEnabled;
    private final String gdprDefaultValue;
    private final boolean consentStringMeansInScope;
    private final GdprService gdprService;
    private final Tcf2Service tcf2Service;
    private final Set<String> eeaCountries;
    private final GeoLocationService geoLocationService;
    private final BidderCatalog bidderCatalog;
    private final IpAddressHelper ipAddressHelper;
    private final Metrics metrics;

    public TcfDefinerService(GdprConfig gdprConfig,
                             Set<String> eeaCountries,
                             GdprService gdprService,
                             Tcf2Service tcf2Service,
                             GeoLocationService geoLocationService,
                             BidderCatalog bidderCatalog,
                             IpAddressHelper ipAddressHelper,
                             Metrics metrics) {

        this.gdprEnabled = gdprConfig != null && BooleanUtils.isNotFalse(gdprConfig.getEnabled());
        this.gdprDefaultValue = gdprConfig != null ? gdprConfig.getDefaultValue() : null;
        this.consentStringMeansInScope = gdprConfig != null
                && BooleanUtils.isTrue(gdprConfig.getConsentStringMeansInScope());
        this.gdprService = Objects.requireNonNull(gdprService);
        this.tcf2Service = Objects.requireNonNull(tcf2Service);
        this.eeaCountries = Objects.requireNonNull(eeaCountries);
        this.geoLocationService = geoLocationService;
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public Future<TcfContext> resolveTcfContext(Privacy privacy,
                                                String country,
                                                String ipAddress,
                                                AccountGdprConfig accountGdprConfig,
                                                MetricName requestType,
                                                Timeout timeout) {

        if (!isGdprEnabled(accountGdprConfig, requestType)) {
            return Future.succeededFuture(TcfContext.empty());
        }

        return toTcfContext(privacy, country, ipAddress, timeout)
                .map(this::updateTcfGeoMetrics);
    }

    public Future<TcfContext> resolveTcfContext(
            Privacy privacy, String ipAddress, AccountGdprConfig accountGdprConfig, Timeout timeout) {

        return resolveTcfContext(privacy, null, ipAddress, accountGdprConfig, null, timeout);
    }

    public Future<TcfResponse<Integer>> resultForVendorIds(Set<Integer> vendorIds, TcfContext tcfContext) {
        return resultForInternal(
                tcfContext,
                country -> createAllowAllTcfResponse(vendorIds, country),
                (consentString, country) -> tcf2Service.permissionsFor(vendorIds, consentString)
                        .map(vendorPermissions -> createVendorIdTcfResponse(vendorPermissions, country)),
                (consentString, country) -> gdprService.resultFor(vendorIds, consentString)
                        .map(vendorPermissions -> createVendorIdTcfResponse(vendorPermissions, country)));
    }

    public Future<TcfResponse<String>> resultForBidderNames(Set<String> bidderNames,
                                                            VendorIdResolver vendorIdResolver,
                                                            TcfContext tcfContext,
                                                            AccountGdprConfig accountGdprConfig) {
        return resultForInternal(
                tcfContext,
                country -> createAllowAllTcfResponse(bidderNames, country),
                (consentString, country) ->
                        tcf2Service.permissionsFor(bidderNames, vendorIdResolver, consentString, accountGdprConfig)
                                .map(vendorPermissions -> createBidderNameTcfResponse(vendorPermissions, country)),
                (consentString, country) ->
                        bidderNameResultFromGdpr(bidderNames, vendorIdResolver, consentString, country));
    }

    public Future<TcfResponse<String>> resultForBidderNames(
            Set<String> bidderNames, TcfContext tcfContext, AccountGdprConfig accountGdprConfig) {

        return resultForBidderNames(bidderNames, VendorIdResolver.of(bidderCatalog), tcfContext, accountGdprConfig);
    }

    private <T> Future<TcfResponse<T>> resultForInternal(
            TcfContext tcfContext,
            Function<String, Future<TcfResponse<T>>> allowAllTcfResponseCreator,
            BiFunction<TCString, String, Future<TcfResponse<T>>> tcf2Strategy,
            BiFunction<String, String, Future<TcfResponse<T>>> gdprStrategy) {

        final GeoInfo geoInfo = tcfContext.getGeoInfo();
        final String country = geoInfo != null ? geoInfo.getCountry() : null;

        if (!inScope(tcfContext)) {
            return allowAllTcfResponseCreator.apply(country);
        }

        final TCString consent = tcfContext.getConsent();

        return consent.getVersion() == 2
                ? tcf2Strategy.apply(consent, country)
                : gdprStrategy.apply(tcfContext.getConsentString(), country);
    }

    private boolean isGdprEnabled(AccountGdprConfig accountGdprConfig, MetricName requestType) {
        final Boolean accountGdprEnabled = accountGdprConfig != null ? accountGdprConfig.getEnabled() : null;
        if (requestType == null) {
            return ObjectUtils.firstNonNull(accountGdprEnabled, gdprEnabled);
        }

        final EnabledForRequestType enabledForRequestType = accountGdprConfig != null
                ? accountGdprConfig.getEnabledForRequestType()
                : null;

        final Boolean enabledForType = enabledForRequestType != null
                ? enabledForRequestType.isEnabledFor(requestType)
                : null;
        return ObjectUtils.firstNonNull(enabledForType, accountGdprEnabled, gdprEnabled);
    }

    private Future<TcfContext> toTcfContext(Privacy privacy, String country, String ipAddress, Timeout timeout) {
        final String consentString = privacy.getConsentString();
        final TCString consent = parseConsentString(consentString);
        final String effectiveIpAddress = maybeMaskIp(ipAddress, consent);

        if (consentStringMeansInScope && isConsentValid(consent)) {
            return Future.succeededFuture(TcfContext.builder()
                    .gdpr(GDPR_ONE)
                    .consentString(consentString)
                    .consent(consent)
                    .ipAddress(effectiveIpAddress)
                    .build());
        }

        final String gdpr = privacy.getGdpr();
        if (StringUtils.isNotEmpty(gdpr)) {
            return Future.succeededFuture(TcfContext.builder()
                    .gdpr(gdpr)
                    .consentString(consentString)
                    .consent(consent)
                    .ipAddress(effectiveIpAddress)
                    .build());
        }

        // from country
        if (country != null) {
            final Boolean inEea = isCountryInEea(country);

            return Future.succeededFuture(TcfContext.builder()
                    .gdpr(gdprFromGeo(inEea))
                    .consentString(consentString)
                    .consent(consent)
                    .inEea(inEea)
                    .ipAddress(effectiveIpAddress)
                    .build());
        }

        // from geo location
        if (ipAddress != null && geoLocationService != null) {
            return geoLocationService.lookup(effectiveIpAddress, timeout)
                    .map(resolvedGeoInfo -> tcfContextFromGeo(
                            resolvedGeoInfo, consentString, consent, effectiveIpAddress))
                    .otherwise(exception -> updateGeoFailedMetricAndReturnDefaultTcfContext(
                            exception, consentString, consent, effectiveIpAddress));
        }

        // use default
        return Future.succeededFuture(defaultTcfContext(consentString, consent, effectiveIpAddress));
    }

    private String maybeMaskIp(String ipAddress, TCString consent) {
        return shouldMaskIp(consent) ? ipAddressHelper.maskIpv4(ipAddress) : ipAddress;
    }

    private static boolean shouldMaskIp(TCString consent) {
        return isConsentValid(consent) && consent.getVersion() == 2 && !consent.getSpecialFeatureOptIns().contains(1);
    }

    private TcfContext tcfContextFromGeo(GeoInfo geoInfo, String consentString, TCString consent, String ipAddress) {
        metrics.updateGeoLocationMetric(true);

        final Boolean inEea = isCountryInEea(geoInfo.getCountry());

        return TcfContext.builder()
                .gdpr(gdprFromGeo(inEea))
                .consentString(consentString)
                .consent(consent)
                .geoInfo(geoInfo)
                .inEea(inEea)
                .ipAddress(ipAddress)
                .build();
    }

    private String gdprFromGeo(Boolean inEea) {
        return inEea == null
                ? gdprDefaultValue
                : inEea ? GDPR_ONE : GDPR_ZERO;
    }

    private Boolean isCountryInEea(String country) {
        return country != null ? eeaCountries.contains(country) : null;
    }

    private TcfContext updateGeoFailedMetricAndReturnDefaultTcfContext(
            Throwable exception, String consentString, TCString consent, String ipAddress) {

        final String message = String.format("Geolocation lookup failed: %s", exception.getMessage());
        logger.warn(message);
        logger.debug(message, exception);

        metrics.updateGeoLocationMetric(false);

        return defaultTcfContext(consentString, consent, ipAddress);
    }

    private TcfContext defaultTcfContext(String consentString, TCString consent, String ipAddress) {
        return TcfContext.builder()
                .gdpr(gdprDefaultValue)
                .consentString(consentString)
                .consent(consent)
                .ipAddress(ipAddress)
                .build();
    }

    private TcfContext updateTcfGeoMetrics(TcfContext tcfContext) {
        if (inScope(tcfContext)) {
            metrics.updatePrivacyTcfGeoMetric(tcfContext.getConsent().getVersion(), tcfContext.getInEea());
        }

        return tcfContext;
    }

    private <T> Future<TcfResponse<T>> createAllowAllTcfResponse(Set<T> keys, String country) {
        return Future.succeededFuture(TcfResponse.of(false, allowAll(keys), country));
    }

    private static TcfResponse<Integer> createVendorIdTcfResponse(
            Collection<VendorPermission> vendorPermissions, String country) {

        return TcfResponse.of(
                true,
                vendorPermissions.stream()
                        .collect(Collectors.toMap(
                                VendorPermission::getVendorId,
                                VendorPermission::getPrivacyEnforcementAction)),
                country);
    }

    private static TcfResponse<String> createBidderNameTcfResponse(
            Collection<VendorPermission> vendorPermissions, String country) {

        return TcfResponse.of(
                true,
                vendorPermissions.stream()
                        .collect(Collectors.toMap(
                                VendorPermission::getBidderName,
                                VendorPermission::getPrivacyEnforcementAction)),
                country);
    }

    private Future<TcfResponse<String>> bidderNameResultFromGdpr(
            Set<String> bidderNames, VendorIdResolver vendorIdResolver, String consentString, String country) {

        final Map<String, Integer> bidderToVendorId = resolveBidderToVendorId(bidderNames, vendorIdResolver);
        final Set<Integer> vendorIds = bidderToVendorId.values().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return gdprService.resultFor(vendorIds, consentString)
                .map(vendorPermissions -> gdprResponseToTcfResponse(vendorPermissions, bidderToVendorId, country));
    }

    private static Map<String, Integer> resolveBidderToVendorId(
            Set<String> bidderNames, VendorIdResolver vendorIdResolver) {

        final Map<String, Integer> bidderToVendorId = new HashMap<>();
        bidderNames.forEach(bidderName -> bidderToVendorId.put(bidderName, vendorIdResolver.resolve(bidderName)));
        return bidderToVendorId;
    }

    private static TcfResponse<String> gdprResponseToTcfResponse(Collection<VendorPermission> vendorPermissions,
                                                                 Map<String, Integer> bidderToVendorId,
                                                                 String country) {

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = new HashMap<>();

        final Map<Integer, Set<String>> vendorIdToBidders = bidderToVendorId.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        for (final VendorPermission vendorPermission : vendorPermissions) {
            final Integer vendorId = vendorPermission.getVendorId();

            if (vendorIdToBidders.containsKey(vendorId)) {
                final PrivacyEnforcementAction action = vendorPermission.getPrivacyEnforcementAction();
                vendorIdToBidders.get(vendorId).forEach(bidderName -> bidderNameToAction.put(bidderName, action));
            }
        }

        // process bidders whose vendorIds weren't resolved
        bidderToVendorId.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .forEach(bidder -> bidderNameToAction.put(bidder, restrictAllButAnalyticsAndAuction()));

        return TcfResponse.of(true, bidderNameToAction, country);
    }

    private static <T> Map<T, PrivacyEnforcementAction> allowAll(Collection<T> identifiers) {
        return identifiers.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> PrivacyEnforcementAction.allowAll()));
    }

    private static boolean inScope(TcfContext tcfContext) {
        return Objects.equals(tcfContext.getGdpr(), GDPR_ONE);
    }

    /**
     * Returns decoded {@link TCString} or {@link TCStringEmpty} in case of empty consent or error occurred.
     * <p>
     * Note: parsing TC string should not fail the entire request, but assume the user does not consent.
     */
    private TCString parseConsentString(String consentString) {
        if (StringUtils.isBlank(consentString)) {
            metrics.updatePrivacyTcfMissingMetric();
            return TCStringEmpty.create();
        }

        final TCString tcString = decodeTcString(consentString);
        if (tcString == null) {
            metrics.updatePrivacyTcfInvalidMetric();
            return TCStringEmpty.create();
        }

        return tcString;
    }

    private TCString decodeTcString(String consentString) {
        try {
            return TCString.decode(consentString);
        } catch (Throwable e) {
            logger.info("Parsing consent string ''{0}'' failed: {1}", consentString, e.getMessage());
            return null;
        }
    }

    public static boolean isConsentValid(TCString consent) {
        return consent != null && !(consent instanceof TCStringEmpty);
    }

    public static boolean isConsentStringValid(String consentString) {
        try {
            TCString.decode(consentString);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static PrivacyEnforcementAction restrictAllButAnalyticsAndAuction() {
        return PrivacyEnforcementAction.builder()
                .removeUserIds(true)
                .maskGeo(true)
                .maskDeviceIp(true)
                .maskDeviceInfo(true)
                .blockAnalyticsReport(false)
                .blockBidderRequest(false)
                .blockPixelSync(true)
                .build();
    }
}
