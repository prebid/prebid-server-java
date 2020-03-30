package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class VendorV2 {

    Integer id;

    Set<Integer> purposes;

    @JsonProperty("legIntPurposes")
    Set<Integer> legIntPurposes;

    @JsonProperty("flexiblePurposes")
    Set<Integer> flexiblePurposes;

    @JsonProperty("specialPurposes")
    Set<Integer> specialPurposes;

    @JsonProperty("features")
    Set<Integer> features;

    @JsonProperty("specialFeatures")
    Set<Integer> specialFeatures;
}

