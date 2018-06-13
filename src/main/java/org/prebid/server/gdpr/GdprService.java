package org.prebid.server.gdpr;

import com.iab.gdpr.ConsentStringParser;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.vendorlist.VendorListService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service provides GDPR support.
 * <p>
 * For more information about GDPR, see https://gdpr.iab.com
 */
public class GdprService {

    private final GeoLocationService geoLocationService;
    private final List<String> eeaCountries;
    private final VendorListService vendorListService;
    private final String gdprDefaultValue;

    public GdprService(GeoLocationService geoLocationService, List<String> eeaCountries,
                       VendorListService vendorListService, String gdprDefaultValue) {
        this.geoLocationService = geoLocationService;
        this.eeaCountries = Objects.requireNonNull(eeaCountries);
        this.vendorListService = Objects.requireNonNull(vendorListService);
        this.gdprDefaultValue = Objects.requireNonNull(gdprDefaultValue);
    }

    /**
     * Returns a map with Vendor ID as a key and GDPR result [true/false] as a value.
     */
    public Future<Map<Integer, Boolean>> resultByVendor(Set<GdprPurpose> purposes, Set<Integer> vendorIds, String gdpr,
                                                        String gdprConsent, String ipAddress) {
        return resolveGdprValue(gdpr, ipAddress)
                .compose(gdprValue -> toResultMap(gdprValue, gdprConsent, purposes, vendorIds));
    }

    /**
     * Determines GDPR value from external GDPR param, geo location or default.
     */
    private Future<String> resolveGdprValue(String gdpr, String ipAddress) {
        final String gdprFromRequest = StringUtils.stripToNull(gdpr);

        final Future<String> result;
        if (gdprFromRequest != null) {
            result = Future.succeededFuture(gdprFromRequest);
        } else if (ipAddress != null && geoLocationService != null) {
            result = geoLocationService.lookup(ipAddress)
                    .map(GeoInfo::getCountry)
                    .map(country -> country == null ? gdprDefaultValue : eeaCountries.contains(country) ? "1" : "0")
                    .otherwise(gdprDefaultValue);
        } else {
            result = Future.succeededFuture(gdprDefaultValue);
        }
        return result;
    }

    /**
     * Analyzes GDPR params and returns a map with GDPR result for each vendor.
     */
    private Future<Map<Integer, Boolean>> toResultMap(String gdpr, String gdprConsent, Set<GdprPurpose> purposes,
                                                      Set<Integer> vendorIds) {
        switch (gdpr) {
            case "0":
                return sameResultFor(vendorIds, true);
            case "1":
                return fromConsent(gdprConsent, purposes, vendorIds);
            default:
                return failWith("The gdpr param must be either 0 or 1, given: %s", gdpr);
        }
    }

    private static Future<Map<Integer, Boolean>> sameResultFor(Set<Integer> vendorIds, boolean result) {
        return Future.succeededFuture(vendorIds.stream().collect(Collectors.toMap(Function.identity(), id -> result)));
    }

    private Future<Map<Integer, Boolean>> fromConsent(String consent, Set<GdprPurpose> purposes,
                                                      Set<Integer> vendorIds) {
        if (StringUtils.isEmpty(consent)) {
            return failWith("The gdpr_consent param is required when gdpr=1");
        }

        final ConsentStringParser parser;
        try {
            parser = new ConsentStringParser(consent);
        } catch (ParseException e) {
            return failWith("The gdpr_consent param '%s' is malformed, parsing error: %s", consent, e.getMessage());
        }

        // consent string confirms user has allowed all purposes
        final Set<Integer> purposeIds = purposes.stream().map(GdprPurpose::getId).collect(Collectors.toSet());
        if (!parser.getAllowedPurposes().containsAll(purposeIds)) {
            return sameResultFor(vendorIds, false);
        }

        return vendorListService.forVersion(parser.getVendorListVersion())
                .map(vendorIdToPurposes -> toResult(vendorIdToPurposes, vendorIds, purposeIds, parser));
    }

    private static Map<Integer, Boolean> toResult(Map<Integer, Set<Integer>> vendorIdToPurposes, Set<Integer> vendorIds,
                                                  Set<Integer> purposeIds, ConsentStringParser parser) {
        final Map<Integer, Boolean> result = new HashMap<>(vendorIds.size());
        for (Integer vendorId : vendorIds) {
            // consent string confirms Vendor is allowed
            final boolean vendorIsAllowed = vendorId != null && parser.isVendorAllowed(vendorId);

            // vendorlist lookup confirms Vendor has all purposes
            final boolean vendorHasAllPurposes;
            if (vendorIsAllowed) {
                final Set<Integer> vendorPurposeIds = vendorIdToPurposes.get(vendorId);
                vendorHasAllPurposes = vendorPurposeIds != null && vendorPurposeIds.containsAll(purposeIds);
            } else {
                vendorHasAllPurposes = false;
            }

            result.put(vendorId, vendorIsAllowed && vendorHasAllPurposes);
        }
        return result;
    }

    private static Future<Map<Integer, Boolean>> failWith(String errorMessageFormat, Object... args) {
        return Future.failedFuture(String.format(errorMessageFormat, args));
    }
}
