package org.prebid.server.bidder.huaweiads;

import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.Set;

public class HuaweiEndpointResolver {

    private static final String CLOSE_COUNTRY = "1";
    private static final Set<String> CHINESE_COUNTRY_CODES = Set.of("CN");
    private static final Set<String> RUSSIAN_COUNTRY_CODES = Set.of("RU");
    private static final Set<String> EUROPEAN_COUNTRY_CODES = Set.of(
            "AX", "AL", "AD", "AU", "AT", "BE", "BA", "BG", "CA", "HR", "CY", "CZ",
            "DK", "EE", "FO", "FI", "FR", "DE", "GI", "GR", "GL", "GG", "VA", "HU",
            "IS", "IE", "IM", "IL", "IT", "JE", "YK", "LV", "LI", "LT", "LU", "MT",
            "MD", "MC", "ME", "NL", "AN", "NZ", "NO", "PL", "PT", "RO", "MF", "VC",
            "SM", "RS", "SX", "SK", "SI", "ES", "SE", "CH", "TR", "UA", "GB", "US",
            "MK", "SJ", "BQ", "PM", "CW");

    private final String endpointUrl;
    private final String closeSiteSelectionByCountry;
    private final String chineseEndpoint;
    private final String russianEndpoint;
    private final String europeanEndpoint;
    private final String asianEndpoint;

    public HuaweiEndpointResolver(String endpoint,
                                  String chineseEndpoint,
                                  String russianEndpoint,
                                  String europeanEndpoint,
                                  String asianEndpoint,
                                  String closeSiteSelectionByCountry) {

        this.endpointUrl = HttpUtil.validateUrl(endpoint);
        this.closeSiteSelectionByCountry = Objects.requireNonNull(closeSiteSelectionByCountry);
        this.chineseEndpoint = HttpUtil.validateUrl(chineseEndpoint);
        this.russianEndpoint = HttpUtil.validateUrl(russianEndpoint);
        this.europeanEndpoint = HttpUtil.validateUrl(europeanEndpoint);
        this.asianEndpoint = HttpUtil.validateUrl(asianEndpoint);
    }

    public String resolve(String countryCode) {
        if (CLOSE_COUNTRY.equals(closeSiteSelectionByCountry)) {
            return endpointUrl;
        } else if (CHINESE_COUNTRY_CODES.contains(countryCode)) {
            return chineseEndpoint;
        } else if (RUSSIAN_COUNTRY_CODES.contains(countryCode)) {
            return russianEndpoint;
        } else if (EUROPEAN_COUNTRY_CODES.contains(countryCode)) {
            return europeanEndpoint;
        } else {
            return asianEndpoint;
        }
    }
}
