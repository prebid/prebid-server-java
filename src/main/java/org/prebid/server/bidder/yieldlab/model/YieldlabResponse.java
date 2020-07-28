package org.prebid.server.bidder.yieldlab.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class YieldlabResponse {

    Integer id;

    BigDecimal price;

    String advertiser;

    @JsonProperty("adsize")
    String adSize;

    Integer pid;

    Integer did;

    String pvid;
}
