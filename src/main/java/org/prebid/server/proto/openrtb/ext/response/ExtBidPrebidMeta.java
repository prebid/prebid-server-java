package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBidPrebidMeta {

    @JsonProperty("advertiserId")
    Integer advertiserId;

    @JsonProperty("advertiserName")
    String advertiserName;

    @JsonProperty("agencyId")
    Integer agencyId;

    @JsonProperty("agencyName")
    String agencyName;

    @JsonProperty("brandId")
    Integer brandId;

    @JsonProperty("brandName")
    String brandName;

    @JsonProperty("mediaType")
    String mediaType;

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("networkName")
    String networkName;

    @JsonProperty("primaryCatId")
    String primaryCatId;

    @JsonProperty("secondaryCatIds")
    List<String> secondaryCatIds;
}
