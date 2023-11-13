package org.prebid.server.bidder.huaweiads;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.geolocation.CountryCodeMapper;

import java.util.Objects;
import java.util.Optional;

public class CountryCodeResolver {

    private final CountryCodeMapper countryCodeMapper;

    public CountryCodeResolver(CountryCodeMapper countryCodeMapper) {
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
    }

    public Optional<String> resolve(BidRequest bidRequest) {
        final Optional<String> countryOfDevice = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getGeo)
                .map(Geo::getCountry)
                .filter(StringUtils::isNotBlank)
                .flatMap(this::convertCountryCode);

        final Optional<String> countryOfUser = Optional.ofNullable(bidRequest.getUser())
                .map(User::getGeo)
                .map(Geo::getCountry)
                .filter(StringUtils::isNotBlank)
                .flatMap(this::convertCountryCode);

        final Optional<String> countryOfMcc = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getMccmnc)
                .filter(StringUtils::isNotBlank)
                //mcc-mnc format
                .map(mccmnc -> mccmnc.split("-")[0])
                .map(countryCodeMapper::mapMccToAlpha2);

        return countryOfDevice
                .or(() -> countryOfUser)
                .or(() -> countryOfMcc);
    }

    private Optional<String> convertCountryCode(String countryCode) {
        return switch (countryCode.length()) {
            case 1 -> Optional.empty();
            case 2 -> Optional.of(countryCode);
            default -> Optional.ofNullable(countryCodeMapper.mapToAlpha2(countryCode));
        };
    }

}
