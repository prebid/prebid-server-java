package org.prebid.server.bidder.sharethrough.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class SharethroughRequestBody {

    @JsonProperty("badv")
    List<String> blockedAdvDomains;

    Long tmax;

    String deadline;

    @JsonIgnore
    Boolean test;

    BigDecimal bidfloor;
}
