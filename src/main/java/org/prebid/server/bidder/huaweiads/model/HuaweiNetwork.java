package org.prebid.server.bidder.huaweiads.model;

import lombok.*;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@Builder
@Setter
@Getter
public class HuaweiNetwork {
    private Integer type;
    private Integer carrier;
    private List<HuaweiCellInfo> cellInfoList;
}
