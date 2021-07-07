package org.prebid.server.geolocation.model.medianet;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.prebid.server.geolocation.model.GeoInfo;

@Mapper
public interface GeoInfoMapper {

    @Mapping(target = "country", source = "countryCode")
    @Mapping(target = "region", source = "stateCode")
    @Mapping(target = "city", source = "city")
    @Mapping(target = "vendor", constant = "netacuity")
    GeoInfo from(IPInfo ipInfo);
}
