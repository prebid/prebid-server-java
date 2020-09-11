package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.GdprInfoWithCountry;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TCStringEmpty;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
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
    private final Metrics metrics;

    public TcfDefinerService(GdprConfig gdprConfig,
                             Set<String> eeaCountries,
                             GdprService gdprService,
                             Tcf2Service tcf2Service,
                             GeoLocationService geoLocationService,
                             BidderCatalog bidderCatalog,
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
        this.metrics = Objects.requireNonNull(metrics);
    }

    // vendorIds and BidderNames can't contain null elements
    public Future<TcfResponse<Integer>> resultForVendorIds(Set<Integer> vendorIds,
                                                           String gdpr,
                                                           String gdprConsent,
                                                           String ipAddress,
                                                           AccountGdprConfig accountGdprConfig,
                                                           Timeout timeout) {
        return resultForInternal(
                gdpr,
                gdprConsent,
                ipAddress,
                accountGdprConfig,
                null,
                timeout,
                country -> createAllowAllTcfResponse(vendorIds, country),
                (consentString, country) -> tcf2Service.permissionsFor(vendorIds, consentString)
                        .map(vendorPermissions -> createVendorIdTcfResponse(vendorPermissions, country)),
                (consentString, country) -> gdprService.resultFor(vendorIds, consentString)
                        .map(vendorPermissions -> createVendorIdTcfResponse(vendorPermissions, country)));
    }

    // vendorIds and BidderNames can't contain null elements
    public Future<TcfResponse<String>> resultForBidderNames(Set<String> bidderNames,
                                                            VendorIdResolver vendorIdResolver,
                                                            String gdpr,
                                                            String gdprConsent,
                                                            String ipAddress,
                                                            AccountGdprConfig accountGdprConfig,
                                                            MetricName requestType,
                                                            Timeout timeout) {
        return resultForInternal(
                gdpr,
                gdprConsent,
                ipAddress,
                accountGdprConfig,
                requestType,
                timeout,
                country -> createAllowAllTcfResponse(bidderNames, country),
                (consentString, country) ->
                        tcf2Service.permissionsFor(bidderNames, vendorIdResolver, consentString, accountGdprConfig)
                                .map(vendorPermissions -> createBidderNameTcfResponse(vendorPermissions, country)),
                (consentString, country) ->
                        bidderNameResultFromGdpr(bidderNames, vendorIdResolver, consentString, country));
    }

    // vendorIds and BidderNames can't contain null elements
    public Future<TcfResponse<String>> resultForBidderNames(Set<String> bidderNames,
                                                            String gdpr,
                                                            String gdprConsent,
                                                            String ipAddress,
                                                            AccountGdprConfig accountGdprConfig,
                                                            Timeout timeout) {
        return resultForBidderNames(
                bidderNames,
                VendorIdResolver.of(bidderCatalog),
                gdpr,
                gdprConsent,
                ipAddress,
                accountGdprConfig,
                null,
                timeout);
    }

    private <T> Future<TcfResponse<T>> resultForInternal(
            String gdpr,
            String gdprConsent,
            String ipAddress,
            AccountGdprConfig accountGdprConfig,
            MetricName requestType,
            Timeout timeout,
            Function<String, Future<TcfResponse<T>>> allowAllTcfResponseCreator,
            BiFunction<TCString, String, Future<TcfResponse<T>>> tcf2Strategy,
            BiFunction<String, String, Future<TcfResponse<T>>> gdprStrategy) {

        if (!isGdprEnabled(accountGdprConfig, requestType)) {
            return allowAllTcfResponseCreator.apply(null);
        }

        return toGdprInfo(gdpr, gdprConsent, ipAddress, timeout)
                .compose(gdprInfoWithCountry ->
                        dispatchToService(gdprInfoWithCountry, allowAllTcfResponseCreator, tcf2Strategy, gdprStrategy));
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

    private Future<GdprInfoWithCountry<String>> toGdprInfo(String gdpr,
                                                           String gdprConsent,
                                                           String ipAddress,
                                                           Timeout timeout) {

        final boolean isInScopeByConsentString = consentStringMeansInScope
                && StringUtils.isNotBlank(gdprConsent)
                && isGdprConsentValid(gdprConsent);
        if (isInScopeByConsentString) {
            return Future.succeededFuture(GdprInfoWithCountry.of(GDPR_ONE, gdprConsent));
        }

        // from request param
        final boolean isValidGdpr = gdpr != null && (gdpr.equals(GDPR_ZERO) || gdpr.equals(GDPR_ONE));
        if (isValidGdpr) {
            return Future.succeededFuture(GdprInfoWithCountry.of(gdpr, gdprConsent));
        }

        // from geo location
        if (ipAddress != null && geoLocationService != null) {
            return geoLocationService.lookup(ipAddress, timeout)
                    .map(GeoInfo::getCountry)
                    .map(resolvedCountry -> createGdprInfoWithCountry(gdprConsent, resolvedCountry))
                    .otherwise(exception -> updateMetricsAndReturnDefault(exception, gdprConsent));
        }

        // use default
        return Future.succeededFuture(defaultGdprInfoWithCountry(gdprConsent));
    }

    private GdprInfoWithCountry<String> createGdprInfoWithCountry(String gdprConsent, String country) {
        metrics.updateGeoLocationMetric(true);

        final Boolean inEea = country != null ? eeaCountries.contains(country) : null;
        final String gdpr = inEea == null
                ? gdprDefaultValue
                : inEea ? GDPR_ONE : GDPR_ZERO;
        return GdprInfoWithCountry.of(gdpr, gdprConsent, country, inEea);
    }

    private GdprInfoWithCountry<String> updateMetricsAndReturnDefault(Throwable exception, String gdprConsent) {
        final String message = String.format("Geolocation lookup failed: %s", exception.getMessage());
        logger.warn(message);
        logger.debug(message, exception);

        metrics.updateGeoLocationMetric(false);
        return defaultGdprInfoWithCountry(gdprConsent);
    }

    private GdprInfoWithCountry<String> defaultGdprInfoWithCountry(String gdprConsent) {
        return GdprInfoWithCountry.of(gdprDefaultValue, gdprConsent);
    }

    private <T> Future<TcfResponse<T>> dispatchToService(
            GdprInfoWithCountry<String> gdprInfoWithCountry,
            Function<String, Future<TcfResponse<T>>> allowAllTcfResponseCreator,
            BiFunction<TCString, String, Future<TcfResponse<T>>> tcf2Strategy,
            BiFunction<String, String, Future<TcfResponse<T>>> gdprStrategy) {

        final String country = gdprInfoWithCountry.getCountry();
        if (!inScope(gdprInfoWithCountry)) {
            return allowAllTcfResponseCreator.apply(country);
        }

        final TCString tcString = parseConsentString(gdprInfoWithCountry.getConsent());
        metrics.updatePrivacyTcfGeoMetric(tcString.getVersion(), gdprInfoWithCountry.getInEea());

        return tcString.getVersion() == 2
                ? tcf2Strategy.apply(tcString, country)
                : gdprStrategy.apply(gdprInfoWithCountry.getConsent(), country);
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

    private static boolean inScope(GdprInfoWithCountry<?> gdprInfo) {
        return Objects.equals(gdprInfo.getGdpr(), GDPR_ONE);
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

    /**
     * Checks if received string can be parsed to vendor consent
     */
    public static boolean isGdprConsentValid(String gdprConsent) {
        try {
            TCString.decode(gdprConsent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
