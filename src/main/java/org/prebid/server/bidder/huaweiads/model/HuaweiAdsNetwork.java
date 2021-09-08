package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Builder
@Setter
@Getter
public class HuaweiAdsNetwork {
    private Integer type;
    private Integer carrier;
    private List<CellInfo> cellInfoList;
}
