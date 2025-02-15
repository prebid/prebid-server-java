package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.RendererData

@ToString(includeNames = true, ignoreNulls = true)
class Meta {

    @JsonProperty("adaptercode")
    String adapterCode
    List<String> advertiserDomains
    Integer advertiserId
    String advertiserName
    Integer agencyId
    String agencyName
    Integer brandId
    String brandName
    String demandSource
    String mediaType
    Integer networkId
    String networkName
    String primaryCategoryId
    String rendererName
    String rendererVersion
    String rendererUrl
    RendererData rendererData
    List<String> secondaryCategoryIdList
}
