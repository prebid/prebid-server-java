package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.EnumSet;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Data
public class VendorV2 {

    Integer id;

    EnumSet<Purpose> purposes;

    @JsonProperty("legIntPurposes")
    EnumSet<Purpose> legIntPurposes;

    @JsonProperty("flexiblePurposes")
    EnumSet<Purpose> flexiblePurposes;

    @JsonProperty("specialPurposes")
    EnumSet<SpecialPurpose> specialPurposes;

    @JsonProperty("features")
    EnumSet<Feature> features;

    @JsonProperty("specialFeatures")
    EnumSet<SpecialFeature> specialFeatures;

    public static VendorV2 empty(Integer id) {
        return VendorV2.builder()
                .id(id)
                .purposes(EnumSet.noneOf(Purpose.class))
                .legIntPurposes(EnumSet.noneOf(Purpose.class))
                .flexiblePurposes(EnumSet.noneOf(Purpose.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
    }
}

