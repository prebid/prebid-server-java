package org.prebid.server.bidder.huaweiads.model;

import lombok.*;

@AllArgsConstructor(staticName = "of")
@Builder
@Setter
@Getter
@ToString
public class HuaweiCellInfo {
    private String mcc;
    private String mnc;
}
