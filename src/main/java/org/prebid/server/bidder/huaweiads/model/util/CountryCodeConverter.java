package org.prebid.server.bidder.huaweiads.model.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CountryCodeConverter {
    private static final String DEFAULT_COUNTRY_NAME = "US";
    private static final Map<String, String> COUNTRY_CODE_MAP = new HashMap<>();

    static {
        COUNTRY_CODE_MAP.put("AND", "AD");
        COUNTRY_CODE_MAP.put("AGO", "AO");
        COUNTRY_CODE_MAP.put("AUT", "AT");
        COUNTRY_CODE_MAP.put("BGD", "BD");
        COUNTRY_CODE_MAP.put("BLR", "BY");
        COUNTRY_CODE_MAP.put("CAF", "CF");
        COUNTRY_CODE_MAP.put("TCD", "TD");
        COUNTRY_CODE_MAP.put("CHL", "CL");
        COUNTRY_CODE_MAP.put("CHN", "CN");
        COUNTRY_CODE_MAP.put("COG", "CG");
        COUNTRY_CODE_MAP.put("COD", "CD");
        COUNTRY_CODE_MAP.put("DNK", "DK");
        COUNTRY_CODE_MAP.put("GNQ", "GQ");
        COUNTRY_CODE_MAP.put("EST", "EE");
        COUNTRY_CODE_MAP.put("GIN", "GN");
        COUNTRY_CODE_MAP.put("GNB", "GW");
        COUNTRY_CODE_MAP.put("GUY", "GY");
        COUNTRY_CODE_MAP.put("IRQ", "IQ");
        COUNTRY_CODE_MAP.put("IRL", "IE");
        COUNTRY_CODE_MAP.put("ISR", "IL");
        COUNTRY_CODE_MAP.put("KAZ", "KZ");
        COUNTRY_CODE_MAP.put("LBY", "LY");
        COUNTRY_CODE_MAP.put("MDG", "MG");
        COUNTRY_CODE_MAP.put("MDV", "MV");
        COUNTRY_CODE_MAP.put("MEX", "MX");
        COUNTRY_CODE_MAP.put("MNE", "ME");
        COUNTRY_CODE_MAP.put("MOZ", "MZ");
        COUNTRY_CODE_MAP.put("PAK", "PK");
        COUNTRY_CODE_MAP.put("PNG", "PG");
        COUNTRY_CODE_MAP.put("PRY", "PY");
        COUNTRY_CODE_MAP.put("POL", "PL");
        COUNTRY_CODE_MAP.put("PRT", "PT");
        COUNTRY_CODE_MAP.put("SRB", "RS");
        COUNTRY_CODE_MAP.put("SVK", "SK");
        COUNTRY_CODE_MAP.put("SVN", "SI");
        COUNTRY_CODE_MAP.put("SWE", "SE");
        COUNTRY_CODE_MAP.put("TUN", "TN");
        COUNTRY_CODE_MAP.put("TUR", "TR");
        COUNTRY_CODE_MAP.put("TKM", "TM");
        COUNTRY_CODE_MAP.put("UKR", "UA");
        COUNTRY_CODE_MAP.put("ARE", "AE");
        COUNTRY_CODE_MAP.put("URY", "UY");
    }

    public static String convertCountryCode(String country) {
        if (country == null || country.isEmpty()) {
            return DEFAULT_COUNTRY_NAME;
        }
        return Optional.ofNullable(COUNTRY_CODE_MAP.get(country)).orElse(country.length() >= 3 ? country.substring(0, 2) : DEFAULT_COUNTRY_NAME);
    }

    public static String getCountryCodeFromMCC(String MCC) {
        String countryCode = Optional.ofNullable(MCC)
                .map(mcc -> mcc.split("-")[0])
                .filter(mcc -> mcc.matches("\\d+"))
                .map(Integer::parseInt)
                .flatMap(mcc -> Optional.ofNullable(MccList.mccMap.get(mcc)))
                .orElse(DEFAULT_COUNTRY_NAME);
        return countryCode.toUpperCase();
    }
}
