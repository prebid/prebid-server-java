package org.prebid.server.hooks.modules.id5.userid.v1.filter;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.modules.id5.userid.v1.config.ValuesFilter;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Objects;
import java.util.Optional;

public class CountryFetchFilter implements FetchActionFilter {

    private final ValuesFilter<String> countryFilter;

    public CountryFetchFilter(ValuesFilter<String> countryFilter) {
        this.countryFilter = Objects.requireNonNull(countryFilter);
    }

    @Override
    public FilterResult shouldInvoke(AuctionRequestPayload payload, AuctionInvocationContext invocationContext) {
        final String country = resolveCountry(payload.bidRequest(), invocationContext);
        if (country == null) {
            return FilterResult.rejected("missing country");
        }
        return countryFilter.isValueAllowed(country)
                ? FilterResult.accepted()
                : FilterResult.rejected("country " + country + " rejected by config");
    }

    private static String resolveCountry(BidRequest bidRequest, AuctionInvocationContext invocationContext) {
        return Optional.ofNullable(invocationContext.auctionContext().getGeoInfo())
                .map(GeoInfo::getCountry)
                .filter(StringUtils::isNotBlank)
                .or(() -> Optional.ofNullable(bidRequest)
                        .map(BidRequest::getDevice)
                        .map(Device::getGeo)
                        .map(Geo::getCountry)
                        .filter(StringUtils::isNotBlank))
                .orElse(null);
    }
}
