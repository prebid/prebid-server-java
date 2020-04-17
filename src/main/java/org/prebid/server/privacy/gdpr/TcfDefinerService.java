package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.GdprInfoWithCountry;
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TCStringEmpty;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.GdprConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TcfDefinerService {

    private static final Logger logger = LoggerFactory.getLogger(TcfDefinerService.class);

    private static final String GDPR_ZERO = "0";
    private static final String GDPR_ONE = "1";

    private final boolean gdprEnabled;
    private final String gdprDefaultValue;
    private final GdprService gdprService;
    private final Tcf2Service tcf2Service;
    private final List<String> eeaCountries;
    private final GeoLocationService geoLocationService;
    private final BidderCatalog bidderCatalog;
    private final Metrics metrics;

    public TcfDefinerService(GdprConfig gdprConfig,
                             List<String> eeaCountries,
                             GdprService gdprService,
                             Tcf2Service tcf2Service,
                             GeoLocationService geoLocationService,
                             BidderCatalog bidderCatalog,
                             Metrics metrics) {

        this.gdprEnabled = gdprConfig != null && BooleanUtils.isNotFalse(gdprConfig.getEnabled());
        this.gdprDefaultValue = gdprConfig != null ? gdprConfig.getDefaultValue() : null;
        this.gdprService = gdprService;
        this.tcf2Service = tcf2Service;
        this.eeaCountries = eeaCountries;
        this.geoLocationService = geoLocationService;
        this.bidderCatalog = bidderCatalog;
        this.metrics = metrics;

        checkForRequiredServices();
    }

    private void checkForRequiredServices() {
        if (gdprEnabled) {
            Objects.requireNonNull(gdprDefaultValue);
            Objects.requireNonNull(gdprService);
            Objects.requireNonNull(tcf2Service);
            Objects.requireNonNull(eeaCountries);
            Objects.requireNonNull(metrics);
        }
    }

    public Future<TcfResponse> resultFor(Set<Integer> vendorIds,
                                         Set<String> bidderNames,
                                         String gdpr,
                                         String gdprConsent,
                                         String ipAddress,
                                         Timeout timeout) {

        return resultFor(vendorIds, bidderNames, gdpr, gdprConsent, ipAddress, null, timeout);
    }

    // vendorIds and BidderNames can't contain null elements
    public Future<TcfResponse> resultFor(Set<Integer> vendorIds,
                                         Set<String> bidderNames,
                                         String gdpr,
                                         String gdprConsent,
                                         String ipAddress,
                                         AccountGdprConfig accountGdprConfig,
                                         Timeout timeout) {

        if (isGdprDisabled(gdprEnabled, accountGdprConfig)) {
            return allowAll(vendorIds, bidderNames, null);
        }

        // TODO Add for another purposes
        final Set<GdprPurpose> gdprPurposes = Collections.singleton(GdprPurpose.informationStorageAndAccess);
        return tcfPurposeForEachVendor(gdprPurposes, vendorIds, bidderNames, gdpr, gdprConsent, ipAddress,
                accountGdprConfig, timeout);
        // TODO FailedFuture
    }

    private boolean isGdprDisabled(Boolean gdprEnabled, AccountGdprConfig accountGdprConfig) {
        final Boolean accountEnabled = accountGdprConfig != null ? accountGdprConfig.getEnabled() : null;
        return BooleanUtils.isFalse(gdprEnabled) || BooleanUtils.isFalse(accountEnabled);
    }

    // vendorIds and BidderNames can't contain null elements
    private Future<TcfResponse> tcfPurposeForEachVendor(Set<GdprPurpose> gdprPurposes,
                                                        Set<Integer> vendorIds,
                                                        Set<String> bidderNames,
                                                        String gdpr,
                                                        String gdprConsent,
                                                        String ipAddress,
                                                        AccountGdprConfig accountGdprConfig,
                                                        Timeout timeout) {

        return toGdprInfo(gdpr, gdprConsent, ipAddress, timeout)
                .compose(gdprInfoWithCountry -> distributeGdprResponse(gdprInfoWithCountry, vendorIds, bidderNames,
                        gdprPurposes, accountGdprConfig));
    }

    private Future<GdprInfoWithCountry<String>> toGdprInfo(
            String gdpr, String gdprConsent, String ipAddress, Timeout timeout) {

        // from request param
        final boolean isValidGdpr = gdpr != null && (gdpr.equals(GDPR_ZERO) || gdpr.equals(GDPR_ONE));
        if (isValidGdpr) {
            return Future.succeededFuture(GdprInfoWithCountry.of(gdpr, gdprConsent, null));
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
        final String gdpr = country == null
                ? gdprDefaultValue
                : eeaCountries.contains(country) ? GDPR_ONE : GDPR_ZERO;
        return GdprInfoWithCountry.of(gdpr, gdprConsent, country);
    }

    private GdprInfoWithCountry<String> updateMetricsAndReturnDefault(Throwable exception, String gdprConsent) {
        logger.info("Geolocation lookup failed", exception);
        metrics.updateGeoLocationMetric(false);
        return defaultGdprInfoWithCountry(gdprConsent);
    }

    private GdprInfoWithCountry<String> defaultGdprInfoWithCountry(String gdprConsent) {
        return GdprInfoWithCountry.of(gdprDefaultValue, gdprConsent, null);
    }

    private Future<TcfResponse> distributeGdprResponse(GdprInfoWithCountry<String> gdprInfo,
                                                       Set<Integer> vendorIds,
                                                       Set<String> bidderNames,
                                                       Set<GdprPurpose> gdprPurposes,
                                                       AccountGdprConfig accountGdprConfig) {

        final String country = gdprInfo.getCountry();
        if (!inScope(gdprInfo)) {
            return allowAll(vendorIds, bidderNames, country);
        }

        // parsing TC string should not fail the entire request, assume the user does not consent
        final TCString tcString = decodeTcString(gdprInfo);
        if (tcString.getVersion() == 2) {
            return resultFromTcf2(vendorIds, bidderNames, gdprPurposes, accountGdprConfig, country, tcString);
        }

        return resultFromGdpr(gdprInfo, vendorIds, bidderNames);
    }

    private Future<TcfResponse> resultFromTcf2(Set<Integer> vendorIds,
                                               Set<String> bidderNames,
                                               Set<GdprPurpose> gdprPurposes,
                                               AccountGdprConfig accountGdprConfig,
                                               String country,
                                               TCString tcString) {

        return tcf2Service.permissionsFor(tcString, vendorIds, bidderNames, gdprPurposes, accountGdprConfig)
                .map(vendorPermissions ->
                        tcf2ResponseToTcfResponse(vendorPermissions, vendorIds, bidderNames, country));
    }

    private TCString decodeTcString(GdprInfoWithCountry<String> gdprInfo) {
        try {
            return TCString.decode(gdprInfo.getConsent());
        } catch (Throwable e) {
            logger.warn("Parsing consent string failed with error: {0}", e.getMessage());
            return new TCStringEmpty(2);
        }
    }

    private static TcfResponse tcf2ResponseToTcfResponse(Collection<VendorPermission> vendorPermissions,
                                                         Set<Integer> vendorIds,
                                                         Set<String> bidderNames,
                                                         String country) {

        final Map<Integer, PrivacyEnforcementAction> vendorIdToGdpr = new HashMap<>();
        final Map<String, PrivacyEnforcementAction> bidderNameToGdpr = new HashMap<>();

        for (VendorPermission vendorPermission : vendorPermissions) {
            final Integer vendorId = vendorPermission.getVendorId();
            if (vendorIds.contains(vendorId)) {
                vendorIdToGdpr.put(vendorId, vendorPermission.getPrivacyEnforcementAction());
            }

            final String bidderName = vendorPermission.getBidderName();
            if (bidderNames.contains(vendorPermission.getBidderName())) {
                bidderNameToGdpr.put(bidderName, vendorPermission.getPrivacyEnforcementAction());
            }
        }

        return TcfResponse.of(true, vendorIdToGdpr, bidderNameToGdpr, country);
    }

    /**
     * Here is a hint to what is going on here.
     * <p>
     * {@link GdprService} knows about vendorIds only and doesn't work with bidder names. That's why it's necessary
     * to resolve vendorId of each bidder name to pass them (vendorIds) all to {@link GdprService} and perform reverse
     * conversion afterwards, i.e. group returned {@link PrivacyEnforcementAction}s by whether they have been
     * requested by vendorId or bidder name (or both) - this is done in
     * {@link #gdprResponseToTcfResponse(Collection, Set, Map, String)} method.
     */
    private Future<TcfResponse> resultFromGdpr(GdprInfoWithCountry<String> gdprInfo,
                                               Set<Integer> vendorIds,
                                               Set<String> bidderNames) {

        final Map<String, Integer> bidderToVendorId = resolveBidderToVendorId(bidderNames);

        return gdprService.resultFor(combineVendorIds(vendorIds, bidderToVendorId), gdprInfo.getConsent())
                .map(vendorPermissions -> gdprResponseToTcfResponse(
                        vendorPermissions, vendorIds, bidderToVendorId, gdprInfo.getCountry()));
    }

    private Map<String, Integer> resolveBidderToVendorId(Set<String> bidderNames) {
        final Map<String, Integer> bidderToVendorId = new HashMap<>();
        for (final String bidderName : bidderNames) {
            final Integer vendorId =
                    bidderCatalog.isActive(bidderName) ? bidderCatalog.vendorIdByName(bidderName) : null;
            bidderToVendorId.put(bidderName, vendorId);
        }
        return bidderToVendorId;
    }

    private static Set<Integer> combineVendorIds(Set<Integer> vendorIds, Map<String, Integer> bidderToVendorId) {
        final Set<Integer> combinedVendorIds = new HashSet<>(vendorIds);
        bidderToVendorId.values().stream().filter(Objects::nonNull).forEach(combinedVendorIds::add);
        return combinedVendorIds;
    }

    private static TcfResponse gdprResponseToTcfResponse(Collection<VendorPermission> vendorPermissions,
                                                         Set<Integer> originalVendorIds,
                                                         Map<String, Integer> bidderToVendorId,
                                                         String country) {

        final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = new HashMap<>();
        final Map<String, PrivacyEnforcementAction> bidderNameToAction = new HashMap<>();

        final Map<Integer, String> vendorIdToBidder = reverseMap(bidderToVendorId);

        for (VendorPermission vendorPermission : vendorPermissions) {
            final Integer vendorId = vendorPermission.getVendorId();

            if (originalVendorIds.contains(vendorId)) {
                vendorIdToAction.put(vendorId, vendorPermission.getPrivacyEnforcementAction());
            }

            if (vendorIdToBidder.containsKey(vendorId)) {
                bidderNameToAction.put(vendorIdToBidder.get(vendorId), vendorPermission.getPrivacyEnforcementAction());
            }
        }

        // process bidders whose vendorIds weren't resolved
        bidderToVendorId.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .forEach(bidder -> bidderNameToAction.put(bidder, restrictAllButAnalyticsAndAuction()));

        return TcfResponse.of(true, vendorIdToAction, bidderNameToAction, country);
    }

    private static Future<TcfResponse> allowAll(Set<Integer> vendorIds, Set<String> bidderNames, String country) {
        return Future.succeededFuture(TcfResponse.of(false, allowAll(vendorIds), allowAll(bidderNames), country));
    }

    private static <T> Map<T, PrivacyEnforcementAction> allowAll(Collection<T> identifiers) {
        return identifiers.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> PrivacyEnforcementAction.allowAll()));
    }

    private static boolean inScope(GdprInfoWithCountry<?> gdprInfo) {
        return Objects.equals(gdprInfo.getGdpr(), GDPR_ONE);
    }

    private static <K, V> Map<V, K> reverseMap(Map<K, V> map) {
        return map.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (first, second) -> first));
    }

    private static PrivacyEnforcementAction restrictAllButAnalyticsAndAuction() {
        return PrivacyEnforcementAction.builder()
                .removeUserBuyerUid(true)
                .maskGeo(true)
                .maskDeviceIp(true)
                .maskDeviceInfo(true)
                .blockAnalyticsReport(false)
                .blockBidderRequest(false)
                .blockPixelSync(true)
                .build();
    }
}
