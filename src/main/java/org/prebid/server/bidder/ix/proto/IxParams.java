package org.prebid.server.bidder.ix.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class IxParams {

    @JsonProperty("siteId")
    String siteId;

    List<Integer> size;
}
