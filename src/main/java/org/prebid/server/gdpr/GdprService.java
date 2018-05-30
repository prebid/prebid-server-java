package org.prebid.server.gdpr;

import com.iab.gdpr.ConsentStringParser;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.gdpr.model.GdprResult;
import org.prebid.server.geolocation.GeoLocationService;

import java.text.ParseException;
import java.util.List;
import java.util.Objects;

/**
 * Service provides GDPR support.
 * <p>
 * For more information about GDPR, see https://gdpr.iab.com
 */
public class GdprService {

    private final GeoLocationService geoLocationService;
    private final List<String> eeaCountries;
    private final String gdprDefaultValue;
    private final Integer gdprHostVendorId;

    public GdprService(GeoLocationService geoLocationService, List<String> eeaCountries, String gdprDefaultValue,
                       Integer gdprHostVendorId) {
        this.geoLocationService = geoLocationService;
        this.eeaCountries = Objects.requireNonNull(eeaCountries);
        this.gdprDefaultValue = Objects.requireNonNull(gdprDefaultValue);
        this.gdprHostVendorId = gdprHostVendorId;
    }

    public Future<GdprResponse> analyze(String gdpr, String gdprConsent, String ip) {
        return toGdprValue(gdpr, ip)
                .map(gdprValue -> toGdprResult(gdprValue, gdprConsent))
                .map(GdprResponse::of);
    }

    private Future<String> toGdprValue(String gdpr, String ip) {
        final String gdprFromRequest = StringUtils.stripToNull(gdpr);

        final Future<String> result;
        if (gdprFromRequest != null) {
            result = Future.succeededFuture(gdprFromRequest);
        } else if (ip != null && geoLocationService != null) {
            result = geoLocationService.lookup(ip)
                    .map(country -> eeaCountries.contains(country) ? "1" : "0")
                    .otherwise(gdprDefaultValue);
        } else {
            result = Future.succeededFuture(gdprDefaultValue);
        }
        return result;
    }

    private GdprResult toGdprResult(String gdpr, String gdprConsent) {
        final GdprResult result;
        switch (gdpr) {
            case "0":
                result = GdprResult.allowed;
                break;
            case "1":
                result = fromConsent(gdprConsent, gdprHostVendorId);
                break;
            default:
                result = GdprResult.error_invalid_gdpr;
        }
        return result;
    }

    private static GdprResult fromConsent(String consent, Integer vendorId) {
        if (StringUtils.isEmpty(consent)) {
            return GdprResult.error_missing_consent;
        }

        final ConsentStringParser parser;
        try {
            parser = new ConsentStringParser(consent);
        } catch (ParseException e) {
            return GdprResult.error_invalid_consent;
        }

        // consent string confirms user has allowed Purpose 1
        if (!parser.isPurposeAllowed(1)) {
            return GdprResult.restricted;
        }

        // consent string confirms the vendor ID for the PBS host company
        if (vendorId == null || !parser.isVendorAllowed(vendorId)) {
            return GdprResult.restricted;
        }

        // FIXME: vendorlist lookup confirms PBS host company as having Purpose 1

        return GdprResult.allowed;
    }
}
