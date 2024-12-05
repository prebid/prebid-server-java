package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBidPrebidMeta {

    @JsonProperty("adaptercode")
    String adapterCode;

    @JsonProperty("advertiserDomains")
    List<String> advertiserDomains;

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

    ObjectNode dchain;

    @JsonProperty("demandSource")
    String demandSource;

    @JsonProperty("mediaType")
    String mediaType;

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("networkName")
    String networkName;

    @JsonProperty("primaryCatId")
    String primaryCategoryId;

    @JsonProperty("rendererName")
    String rendererName;

    @JsonProperty("rendererVersion")
    String rendererVersion;

    @JsonProperty("rendererUrl")
    String rendererUrl;

    @JsonProperty("rendererData")
    ObjectNode rendererData;

    @JsonProperty("secondaryCatIds")
    List<String> secondaryCategoryIdList;

    String seat;

}
