package org.prebid.server.privacy.gdpr;

import com.iab.gdpr.consent.VendorConsent;
import com.iab.gdpr.consent.VendorConsentDecoder;
import com.iab.gdpr.exception.VendorConsentParseException;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.GdprResponse;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service provides GDPR support.
 * <p>
 * For more information about GDPR, see https://gdpr.iab.com site.
 */
public class GdprService {

    private static final Logger logger = LoggerFactory.getLogger(GdprService.class);

    private static final String GDPR_ZERO = "0";
    private static final String GDPR_ONE = "1";

    private final GeoLocationService geoLocationService;
    private final Metrics metrics;
    private final List<String> eeaCountries;
    private final VendorListService vendorListService;
    private final String gdprDefaultValue;

    public GdprService(GeoLocationService geoLocationService, Metrics metrics, VendorListService vendorListService,
                       List<String> eeaCountries, String gdprDefaultValue) {
        this.geoLocationService = geoLocationService;
        this.metrics = Objects.requireNonNull(metrics);
        this.eeaCountries = Objects.requireNonNull(eeaCountries);
        this.vendorListService = Objects.requireNonNull(vendorListService);
        this.gdprDefaultValue = Objects.requireNonNull(gdprDefaultValue);
    }

    /**
     * Returns the verdict about enforcing of GDPR processing:
     * <p>
     * - If GDPR from request is valid - returns TRUE if it equals to 1, otherwise FALSE.
     * - If GDPR doesn't enforced by account - returns FALSE.
     * - If there are no GDPR enforced vendors - returns FALSE.
     */
    public boolean isGdprEnforced(String gdpr, Boolean isGdprEnforcedByAccount, Set<Integer> vendorIds) {
        return isValidGdpr(gdpr)
                ? gdpr.equals(GDPR_ONE)
                : ObjectUtils.defaultIfNull(isGdprEnforcedByAccount, !vendorIds.isEmpty());
    }

    /**
     * Implements "each vendor has all given purposes" checking strategy.
     * <p>
     * Returns {@link GdprResponse} which handles information about a map with Vendor ID as a key and GDPR result
     * [true/false] and country user comes from.
     */
    public Future<GdprResponse> resultByVendor(Set<GdprPurpose> purposes, Set<Integer> vendorIds, String gdpr,
                                               String gdprConsent, String ipAddress, Timeout timeout) {
        return toGdprInfo(gdpr, gdprConsent, ipAddress, timeout)
                .compose(gdprInfo -> toResultByVendor(gdprInfo, vendorIds,
                        purposesForVendorCheck(gdprInfo, purposes), GdprService::verdictForVendorHasAllGivenPurposes)
                        .map(vendorIdToResult ->
                                GdprResponse.of(inScope(gdprInfo), vendorIdToResult, gdprInfo.getCountry())));
    }

    /**
     * Implements "consent string has all vendor purposes" checking strategy.
     * <p>
     * Returns {@link GdprResponse} which handles information about a map with Vendor ID as a key and GDPR result
     * [true/false] and country user comes from.
     * <p>
     * GDPR purposes will be fetched from consent string.
     */
    public Future<GdprResponse> resultByVendor(Set<Integer> vendorIds, String gdpr,
                                               String gdprConsent, String ipAddress, Timeout timeout) {
        return toGdprInfo(gdpr, gdprConsent, ipAddress, timeout)
                .compose(gdprInfo -> toResultByVendor(gdprInfo, vendorIds,
                        purposesForConsentCheck(gdprInfo), GdprService::verdictForConsentHasAllVendorPurposes)
                        .map(vendorIdToResult ->
                                GdprResponse.of(inScope(gdprInfo), vendorIdToResult, gdprInfo.getCountry())));
    }

    /**
     * Returns purpose IDs from the given {@link GdprPurpose} collection or null if it is not needed by flow.
     */
    private Set<Integer> purposesForVendorCheck(GdprInfoWithCountry gdprInfo, Set<GdprPurpose> purposes) {
        return inScope(gdprInfo) && gdprInfo.getVendorConsent() != null
                ? purposes.stream().map(GdprPurpose::getId).collect(Collectors.toSet())
                : null;
    }

    /**
     * Confirms vendor has all given purposes.
     */
    private static boolean verdictForVendorHasAllGivenPurposes(Set<Integer> givenPurposeIds,
                                                               Set<Integer> vendorPurposeIds) {
        return vendorPurposeIds.containsAll(givenPurposeIds);
    }

    /**
     * Returns purpose IDs from consent string or null if it is not needed by flow.
     */
    private Set<Integer> purposesForConsentCheck(GdprInfoWithCountry gdprInfo) {
        return inScope(gdprInfo) && gdprInfo.getVendorConsent() != null
                ? getAllowedPurposeIdsFromConsent(gdprInfo.getVendorConsent())
                : null;
    }

    /**
     * Confirms consent purposes (as superset) contains all vendor purposes (as subset).
     */
    private static Boolean verdictForConsentHasAllVendorPurposes(Set<Integer> consentPurposeIds,
                                                                 Set<Integer> vendorPurposeIds) {
        return consentPurposeIds.containsAll(vendorPurposeIds);
    }

    private Future<Map<Integer, Boolean>> toResultByVendor(
            GdprInfoWithCountry gdprInfo, Set<Integer> vendorIds, Set<Integer> purposeIds,
            BiFunction<Set<Integer>, Set<Integer>, Boolean> verdictForPurposes) {

        if (!inScope(gdprInfo)) {
            return sameResultFor(vendorIds, true); // allow all vendors
        }

        final VendorConsent vendorConsent = gdprInfo.getVendorConsent();
        if (vendorConsent == null) {
            return sameResultFor(vendorIds, false); // consent is broken
        }

        final Set<Integer> allowedPurposeIds = getAllowedPurposeIdsFromConsent(vendorConsent);

        // consent string confirms user has allowed given purposes
        if (allowedPurposeIds != purposeIds && !allowedPurposeIds.containsAll(purposeIds)) {
            return sameResultFor(vendorIds, false);
        }

        return vendorListService.forVersion(vendorConsent.getVendorListVersion())
                .map(vendorIdToPurposes ->
                        toResult(vendorIdToPurposes, vendorIds, vendorConsent, purposeIds, verdictForPurposes));
    }

    /**
     * Retrieves allowed purpose ids from consent string. Throws {@link InvalidRequestException} in case of
     * gdpr sdk throws {@link ArrayIndexOutOfBoundsException} when consent string is not valid.
     */
    private static Set<Integer> getAllowedPurposeIdsFromConsent(VendorConsent vendorConsent) {
        try {
            return vendorConsent.getAllowedPurposeIds();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InvalidRequestException(
                    "Error when retrieving allowed purpose ids in a reason of invalid consent string");
        }
    }

    /**
     * Creates the same result for all given vendors.
     */
    private static Future<Map<Integer, Boolean>> sameResultFor(Set<Integer> vendorIds, boolean result) {
        return Future.succeededFuture(vendorIds.stream()
                .collect(Collectors.toMap(Function.identity(), id -> result)));
    }

    /**
     * Processes {@link VendorListService} response and returns GDPR result by vendor ID.
     */
    private static Map<Integer, Boolean> toResult(
            Map<Integer, Set<Integer>> vendorIdToPurposes, Set<Integer> vendorIds, VendorConsent vendorConsent,
            Set<Integer> purposeIds, BiFunction<Set<Integer>, Set<Integer>, Boolean> verdictForPurposes) {

        final Map<Integer, Boolean> result = new HashMap<>(vendorIds.size());
        for (Integer vendorId : vendorIds) {
            // confirm consent is allowed the vendor
            final boolean vendorIsAllowed = isVendorAllowed(vendorConsent, vendorIdToPurposes, vendorId);

            // confirm purposes
            final boolean purposesAreMatched = vendorIsAllowed
                    && verdictForPurposes.apply(purposeIds, vendorIdToPurposes.get(vendorId));

            result.put(vendorId, vendorIsAllowed && purposesAreMatched);
        }
        return result;
    }

    /**
     * Checks if vendorId is in list of allowed vendors in consent string. Throws {@link InvalidRequestException}
     * in case of gdpr sdk throws exception when consent string is not valid.
     */
    private static boolean isVendorAllowed(VendorConsent vendorConsent, Map<Integer, Set<Integer>> vendorIdToPurposes,
                                           Integer vendorId) {
        if (vendorId == null || !vendorIdToPurposes.containsKey(vendorId)) {
            return false;
        }
        try {
            return vendorConsent.isVendorAllowed(vendorId);
        } catch (ArrayIndexOutOfBoundsException | VendorConsentParseException e) {
            throw new InvalidRequestException(
                    "Error when checking if vendor is allowed in a reason of invalid consent string");
        }
    }

    /**
     * Resolves GDPR internal flag and returns {@link GdprInfoWithCountry} model.
     */
    private Future<GdprInfoWithCountry> toGdprInfo(String gdpr, String gdprConsent, String ipAddress, Timeout timeout) {
        // from request param
        if (isValidGdpr(gdpr)) {
            return Future.succeededFuture(GdprInfoWithCountry.of(gdpr, vendorConsentFrom(gdpr, gdprConsent), null));
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

    /**
     * Checks GDPR flag has valid value.
     */
    private boolean isValidGdpr(String gdpr) {
        return gdpr != null && (gdpr.equals(GDPR_ZERO) || gdpr.equals(GDPR_ONE));
    }

    /**
     * Parses consent string to {@link VendorConsent} model. Returns null if:
     * <p>
     * - GDPR flag is not equal to 1
     * <p>
     * - consent string is missing
     * <p>
     * - parsing of consent string is failed
     */
    private VendorConsent vendorConsentFrom(String gdpr, String gdprConsent) {
        if (!Objects.equals(gdpr, GDPR_ONE) || StringUtils.isEmpty(gdprConsent)) {
            return null;
        }
        try {
            return VendorConsentDecoder.fromBase64String(gdprConsent);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Parsing consent string failed with error: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Updates Geo {@link Metrics} and creates {@link GdprInfoWithCountry} which GDPR value depends on if country is
     * in eea list.
     */
    private GdprInfoWithCountry createGdprInfoWithCountry(String gdprConsent, String country) {
        metrics.updateGeoLocationMetric(true);
        final String gdpr = country == null ? gdprDefaultValue : eeaCountries.contains(country) ? GDPR_ONE : GDPR_ZERO;
        return GdprInfoWithCountry.of(gdpr, vendorConsentFrom(gdpr, gdprConsent), country);
    }

    /**
     * Updates Geo {@link Metrics} and returns default {@link GdprInfoWithCountry}.
     */
    private GdprInfoWithCountry updateMetricsAndReturnDefault(Throwable exception, String gdprConsent) {
        logger.info("Geolocation lookup failed", exception);
        metrics.updateGeoLocationMetric(false);
        return defaultGdprInfoWithCountry(gdprConsent);
    }

    /**
     * Creates default {@link GdprInfoWithCountry} with null country, default GDPR value and GDPR consent from request.
     */
    private GdprInfoWithCountry defaultGdprInfoWithCountry(String gdprConsent) {
        return GdprInfoWithCountry.of(gdprDefaultValue, vendorConsentFrom(gdprDefaultValue, gdprConsent), null);
    }

    /**
     * Determines if user is in GDPR scope.
     */
    private static boolean inScope(GdprInfoWithCountry gdprInfo) {
        return Objects.equals(gdprInfo.getGdpr(), GDPR_ONE);
    }

    /**
     * Internal class for holding GDPR information and country.
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class GdprInfoWithCountry {

        String gdpr;

        VendorConsent vendorConsent;

        String country;
    }
}
