package org.prebid.server.gdpr;

import com.iab.gdpr.ConsentStringParser;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.model.GdprResponse;
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

    public static final Logger logger = LoggerFactory.getLogger(GdprService.class);

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
     * Returns {@link GdprResponse} which handles information about a map with Vendor ID as a key and GDPR result
     * [true/false] and country user comes from.
     */
    public Future<GdprResponse> resultByVendor(Set<GdprPurpose> purposes, Set<Integer> vendorIds, String gdpr,
                                               String gdprConsent, String ipAddress) {
        return resolveGdprWithCountryValue(gdpr, ipAddress)
                .compose(gdprWithCountry -> toGdprResponse(gdprWithCountry.getGdpr(), gdprConsent, purposes, vendorIds,
                        gdprWithCountry.getCountry()));
    }

    /**
     * Determines GDPR value from external GDPR param, geo location or default.
     */
    private Future<GdprWithCountry> resolveGdprWithCountryValue(String gdpr, String ipAddress) {
        final String gdprFromRequest = StringUtils.stripToNull(gdpr);

        final Future<GdprWithCountry> result;
        if (isValidGdpr(gdprFromRequest)) {
            result = Future.succeededFuture(GdprWithCountry.of(gdprFromRequest, null));
        } else if (ipAddress != null && geoLocationService != null) {
            result = geoLocationService.lookup(ipAddress)
                    .map(GeoInfo::getCountry)
                    .map(this::createGdprWithCountry)
                    .otherwise(GdprWithCountry.of(gdprDefaultValue, null));
        } else {
            result = Future.succeededFuture(GdprWithCountry.of(gdprDefaultValue, null));
        }
        return result;
    }

    /**
     * Returns flag if gdpr has valid value '0' or '1'.
     */
    private boolean isValidGdpr(String gdprFromRequest) {
        return gdprFromRequest != null && (gdprFromRequest.equals("0") || gdprFromRequest.equals("1"));
    }

    /**
     * Creates {@link GdprWithCountry} which gdpr value depends on if country is in eea list.
     */
    private GdprWithCountry createGdprWithCountry(String country) {
        final String gdpr = country == null ? gdprDefaultValue : eeaCountries.contains(country) ? "1" : "0";
        return GdprWithCountry.of(gdpr, country);
    }

    /**
     * Analyzes GDPR params and returns a {@link GdprResponse} with map of gdpr result for each vendor.
     */
    private Future<GdprResponse> toGdprResponse(String gdpr, String gdprConsent, Set<GdprPurpose> purposes,
                                                Set<Integer> vendorIds, String country) {
        final Future<Map<Integer, Boolean>> vendorIdsToGdpr;
        switch (gdpr) {
            case "0":
                vendorIdsToGdpr = sameResultFor(vendorIds, true);
                break;
            case "1":
                vendorIdsToGdpr = fromConsent(gdprConsent, purposes, vendorIds);
                break;
            default:
                return Future.failedFuture(String.format("The gdpr param must be either 0 or 1, given: %s", gdpr));
        }
        return vendorIdsToGdpr.map(vendorsToGdpr -> GdprResponse.of(vendorsToGdpr, country));
    }

    private static Future<Map<Integer, Boolean>> sameResultFor(Set<Integer> vendorIds, boolean result) {
        return Future.succeededFuture(vendorIds.stream().collect(Collectors.toMap(Function.identity(), id -> result)));
    }

    private Future<Map<Integer, Boolean>> fromConsent(String consent, Set<GdprPurpose> purposes,
                                                      Set<Integer> vendorIds) {
        if (StringUtils.isEmpty(consent)) {
            return sameResultFor(vendorIds, false);
        }

        final ConsentStringParser parser;
        try {
            parser = new ConsentStringParser(consent);
        } catch (ParseException e) {
            logger.warn("Error occurred during parsing consent string {0}", e.getMessage());
            return sameResultFor(vendorIds, false);
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

    /**
     * Internal class for holding gdpr and country values.
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class GdprWithCountry {
        String gdpr;
        String country;
    }
}
