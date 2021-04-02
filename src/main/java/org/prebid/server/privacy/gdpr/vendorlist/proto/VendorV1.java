package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.EnumSet;

@Value(staticConstructor = "of")
public class VendorV1 {

    Integer id;

    @JsonProperty("purposeIds")
    EnumSet<Purpose> purposeIds;

    @JsonProperty("legIntPurposeIds")
    EnumSet<Purpose> legIntPurposeIds;

    public EnumSet<Purpose> combinedPurposes() {
        final EnumSet<Purpose> combinedPurposes = EnumSet.noneOf(Purpose.class);
        if (purposeIds != null) {
            combinedPurposes.addAll(purposeIds);
        }
        if (legIntPurposeIds != null) {
            combinedPurposes.addAll(legIntPurposeIds);
        }

        return combinedPurposes;
    }
}
