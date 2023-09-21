package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class CellInfo {

    String mcc;

    String mnc;

}
