package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class HuaweiNetwork {

    Integer type;

    Integer carrier;

    List<HuaweiCellInfo> cellInfoList;
}

