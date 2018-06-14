package org.prebid.server.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Set;

@AllArgsConstructor(staticName = "of")
@Value
public class Vendor {

    Integer id;

    @JsonProperty("purposeIds")
    Set<Integer> purposeIds;
}
