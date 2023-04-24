package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Network {

    Integer type;

    Integer carrier;

    List<CellInfo> cellInfo;
}
