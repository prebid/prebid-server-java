package org.prebid.server.bidder.huaweiads.model.request;

import org.apache.commons.lang3.EnumUtils;
import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;

import java.util.Optional;

public enum CountryCode {

    AND("AD"), AGO("AO"), AUT("AT"), BGD("BD"), BLR("BY"), CAF("CF"), CHD("TD"), CHL("CL"), CHN("CN"), COG("CG"),
    COD("CD"), DNK("DK"), GNQ("GQ"), EST("EE"), GIN("GN"), GNB("GW"), GUY("GY"), IRQ("IQ"), IRL("IE"), ISR("IL"),
    KAZ("KZ"), LBY("LY"), MDG("MG"), MDV("MV"), MEX("MX"), MNE("ME"), MOZ("MZ"), PAK("PK"), PNG("PG"), PRY("PY"),
    POL("PL"), PRT("PT"), SRB("RS"), SVK("SK"), SVN("SI"), SWE("SE"), TUN("TN"), TUR("TR"), TKM("TM"), UKR("UA"),
    ARE("AE"), URY("UY");

    private final String code;

    CountryCode(String code) {
        this.code = code;
    }

    public static String convertCountryCode(String country) {
        if (country == null || country.isEmpty()) {
            return HuaweiAdsConstants.DEFAULT_COUNTRY_NAME;
        } else if (country.length() >= 3 && !EnumUtils.isValidEnum(CountryCode.class, country)) {
            return country.substring(0, 2);
        }
        String countryCode = CountryCode.valueOf(country).getCode();

        return countryCode != null ? countryCode : HuaweiAdsConstants.DEFAULT_COUNTRY_NAME;
    }

    public static Mcc getCountryCodeFromMCC(String mccValue) {
        return Optional.ofNullable(mccValue)
                .map(mcc -> mcc.split("-")[0])
                .filter(mcc -> mcc.matches("\\d+"))
                .map(Integer::parseInt)
                .map(Mcc::fromCode)
                .orElse(Mcc.ZA);
    }

    public String getCode() {
        return code;
    }
}
