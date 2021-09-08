package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor(staticName = "of")
@Builder
@Setter
@Getter
public class CellInfo {
    private String mcc;
    private String mnc;
}
