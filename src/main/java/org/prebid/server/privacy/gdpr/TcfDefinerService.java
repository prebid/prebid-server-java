package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.GdprInfoWithCountry;
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.GdprConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
    private final Metrics metrics;

    public TcfDefinerService(GdprConfig gdprConfig,
                             List<String> eeaCountries,
                             GdprService gdprService,
                             Tcf2Service tcf2Service,
                             GeoLocationService geoLocationService,
                             Metrics metrics) {
        this.gdprEnabled = gdprConfig != null && BooleanUtils.isNotFalse(gdprConfig.getEnabled());
        this.gdprDefaultValue = gdprConfig != null ? gdprConfig.getDefaultValue() : null;
        this.gdprService = gdprService;
        this.tcf2Service = tcf2Service;
        this.eeaCountries = eeaCountries;
        this.geoLocationService = geoLocationService;
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

    // vendorIds and BidderNames can't contain null elements
    public Future<TcfResponse> resultFor(Set<Integer> vendorIds,
                                         Set<String> bidderNames,
                                         String gdpr,
                                         String gdprConsent,
                                         String ipAddress,
                                         Timeout timeout) {
        if (BooleanUtils.isFalse(gdprEnabled)) {
            return allowAll(vendorIds, bidderNames, null);
        }

        // TODO Add for another purposes
        final Set<GdprPurpose> gdprPurposes = Collections.singleton(GdprPurpose.adSelectionAndDeliveryAndReporting);
        return tcfPurposeForEachVendor(gdprPurposes, vendorIds, bidderNames, gdpr, gdprConsent, ipAddress, timeout);
    }

    // vendorIds and BidderNames can't contain null elements
    private Future<TcfResponse> tcfPurposeForEachVendor(Set<GdprPurpose> gdprPurposes,
                                                        Set<Integer> vendorIds,
                                                        Set<String> bidderNames,
                                                        String gdpr,
                                                        String gdprConsent,
                                                        String ipAddress,
                                                        Timeout timeout) {
        return toGdprInfo(gdpr, gdprConsent, ipAddress, timeout)
                .compose(gdprInfoWithCountry -> distributeGdprResponse(gdprInfoWithCountry, vendorIds, bidderNames,
                        gdprPurposes));
    }

    private Future<GdprInfoWithCountry<String>> toGdprInfo(String gdpr, String gdprConsent, String ipAddress,
                                                           Timeout timeout) {
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
                                                       Set<GdprPurpose> gdprPurposes) {
        final String country = gdprInfo.getCountry();
        if (!inScope(gdprInfo)) {
            return allowAll(vendorIds, bidderNames, country);
        }

        final TCString decodedTcf = TCString.decode(gdprInfo.getConsent());
        if (decodedTcf.getVersion() == 2) {
            return tcf2Service.permissionsFor(decodedTcf, vendorIds, bidderNames, gdprPurposes)
                    .map(vendorPermissions -> prepareTcfResponse(vendorIds, bidderNames, vendorPermissions, country));
        } else {
            return gdprService.resultFor(gdprPurposes, vendorIds, bidderNames, gdprInfo)
                    .map(vendorPermissions -> prepareTcfResponse(vendorIds, bidderNames, vendorPermissions, country));
        }
    }

    private TcfResponse prepareTcfResponse(Set<Integer> vendorIds,
                                           Set<String> bidderNames,
                                           Collection<VendorPermission> vendorPermissions,
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

    private Future<TcfResponse> allowAll(Set<Integer> vendorIds, Set<String> bidderNames, String country) {
        return Future.succeededFuture(TcfResponse.of(false, allowAll(vendorIds), allowAll(bidderNames), country));
    }

    private <T> Map<T, PrivacyEnforcementAction> allowAll(Collection<T> vendorIdentifier) {
        return vendorIdentifier.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> PrivacyEnforcementAction.allowAll()));
    }

    private static boolean inScope(GdprInfoWithCountry<?> gdprInfo) {
        return Objects.equals(gdprInfo.getGdpr(), GDPR_ONE);
    }
}

