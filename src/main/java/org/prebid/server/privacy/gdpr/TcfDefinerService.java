package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.BitSetIntIterable;
import com.iabtcf.utils.IntIterable;
import com.iabtcf.v2.PublisherRestriction;
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
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.GdprConfig;

import java.time.Instant;
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
        this.gdprService = Objects.requireNonNull(gdprService);
        this.tcf2Service = Objects.requireNonNull(tcf2Service);
        this.eeaCountries = Objects.requireNonNull(eeaCountries);
        this.geoLocationService = geoLocationService;
        this.metrics = Objects.requireNonNull(metrics);
    }

    // vendorIds and BidderNames can't contain null elements
    public Future<TcfResponse> resultFor(Set<Integer> vendorIds,
                                         Set<String> bidderNames,
                                         String gdpr,
                                         String gdprConsent,
                                         String ipAddress,
                                         Account account,
                                         Timeout timeout) {
        if (isGdprDisabled(gdprEnabled, account)) {
            return allowAll(vendorIds, bidderNames, null);
        }

        // TODO Add for another purposes
        final Set<GdprPurpose> gdprPurposes = Collections.singleton(GdprPurpose.informationStorageAndAccess);
        return tcfPurposeForEachVendor(gdprPurposes, vendorIds, bidderNames, gdpr, gdprConsent, ipAddress, timeout);
    }

    private boolean isGdprDisabled(Boolean gdprEnabled, Account account) {
        final Boolean legacyEnforcement = account == null ? null : account.getEnforceGdpr();
        final AccountGdprConfig gdpr = account == null ? null : account.getGdpr();
        final Boolean tcf2Enforcement = gdpr == null ? null : BooleanUtils.isNotFalse(gdpr.getEnabled());

        if (legacyEnforcement == null && tcf2Enforcement == null) {
            return BooleanUtils.isFalse(gdprEnabled);
        }

        return BooleanUtils.isFalse(tcf2Enforcement) || BooleanUtils.isFalse(legacyEnforcement);
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

        // parsing TC string should not fail the entire request, assume the user does not consent
        TCString tcString;
        try {
            tcString = TCString.decode(gdprInfo.getConsent());
        } catch (Throwable e) {
            logger.warn("Parsing consent string failed with error: {0}", e.getMessage());
            tcString = new TCStringEmpty(2);
        }

        if (tcString.getVersion() == 2) {
            return tcf2Service.permissionsFor(tcString, vendorIds, bidderNames, gdprPurposes)
                    .map(vendorPermissions -> toTcfResponse(vendorIds, bidderNames, vendorPermissions, country));
        }

        return gdprService.resultFor(gdprPurposes, vendorIds, bidderNames, gdprInfo)
                .map(vendorPermissions -> toTcfResponse(vendorIds, bidderNames, vendorPermissions, country));
    }

    private static TcfResponse toTcfResponse(Set<Integer> vendorIds,
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

    static class TCStringEmpty implements TCString {

        private int version;

        TCStringEmpty(int version) {
            this.version = version;
        }

        @Override
        public int getVersion() {
            return version;
        }

        @Override
        public Instant getCreated() {
            return null;
        }

        @Override
        public Instant getLastUpdated() {
            return null;
        }

        @Override
        public int getCmpId() {
            return 0;
        }

        @Override
        public int getCmpVersion() {
            return 0;
        }

        @Override
        public int getConsentScreen() {
            return 0;
        }

        @Override
        public String getConsentLanguage() {
            return null;
        }

        @Override
        public int getVendorListVersion() {
            return 0;
        }

        @Override
        public IntIterable getPurposesConsent() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getVendorConsent() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public boolean getDefaultVendorConsent() {
            return false;
        }

        @Override
        public int getTcfPolicyVersion() {
            return 0;
        }

        @Override
        public boolean isServiceSpecific() {
            return false;
        }

        @Override
        public boolean getUseNonStandardStacks() {
            return false;
        }

        @Override
        public IntIterable getSpecialFeatureOptIns() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getPurposesLITransparency() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public boolean getPurposeOneTreatment() {
            return false;
        }

        @Override
        public String getPublisherCC() {
            return null;
        }

        @Override
        public IntIterable getVendorLegitimateInterest() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public List<PublisherRestriction> getPublisherRestrictions() {
            return null;
        }

        @Override
        public IntIterable getAllowedVendors() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getDisclosedVendors() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getPubPurposesConsent() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getPubPurposesLITransparency() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getCustomPurposesConsent() {
            return BitSetIntIterable.EMPTY;
        }

        @Override
        public IntIterable getCustomPurposesLITransparency() {
            return BitSetIntIterable.EMPTY;
        }
    }
}
