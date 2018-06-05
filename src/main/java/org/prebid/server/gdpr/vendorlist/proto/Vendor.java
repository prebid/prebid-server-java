package org.prebid.server.gdpr.vendorlist.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class Vendor {

    int id;

    String name;

    @JsonProperty("purposeIds")
    List<Integer> purposeIds;
}
