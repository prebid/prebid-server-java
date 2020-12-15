package org.prebid.server.bidder.yieldlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class YieldlabResponse {

    Integer id;

    Double price;

    String advertiser;

    @JsonProperty("adsize")
    String adSize;

    Integer pid;

    Integer did;

    String pvid;
}
