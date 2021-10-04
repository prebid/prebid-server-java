package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
@Builder
public class HuaweiNetwork {

    Integer type;
    Integer carrier;
    List<HuaweiCellInfo> cellInfoList;
}
