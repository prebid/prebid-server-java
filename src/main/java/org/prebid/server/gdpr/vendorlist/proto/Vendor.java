package org.prebid.server.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor(staticName = "of")
@Value
public class Vendor {

    Integer id;

    @JsonProperty("purposeIds")
    Set<Integer> purposeIds;

    @JsonProperty("legIntPurposeIds")
    Set<Integer> legIntPurposeIds;

    public Set<Integer> combinedPurposes() {

        return Stream.of(purposeIds != null ? purposeIds : Collections.<Integer>emptySet(),
                legIntPurposeIds != null ? legIntPurposeIds : Collections.<Integer>emptySet())
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }
}
