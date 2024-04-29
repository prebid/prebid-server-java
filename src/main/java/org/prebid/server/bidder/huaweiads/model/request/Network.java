package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Network {

    Integer type;

    Integer carrier;

    @JsonProperty("cellInfo")
    List<CellInfo> cellInfo;
}
