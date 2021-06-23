package org.prebid.server.privacy.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.EnumSet;

@Value(staticConstructor = "of")
public class VendorV1 {

    Integer id;

    @JsonProperty("purposeIds")
    EnumSet<PurposeCode> purposeIds;

    @JsonProperty("legIntPurposeIds")
    EnumSet<PurposeCode> legIntPurposeIds;

    public EnumSet<PurposeCode> combinedPurposes() {
        final EnumSet<PurposeCode> combinedPurposes = EnumSet.noneOf(PurposeCode.class);
        if (purposeIds != null) {
            combinedPurposes.addAll(purposeIds);
        }
        if (legIntPurposeIds != null) {
            combinedPurposes.addAll(legIntPurposeIds);
        }

        return combinedPurposes;
    }
}
