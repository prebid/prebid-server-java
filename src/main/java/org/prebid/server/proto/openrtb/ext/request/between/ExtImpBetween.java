package org.prebid.server.proto.openrtb.ext.request.between;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBetween {

    String host;

    String publisherId;

    BigDecimal bidFloor;

    String bidFloorCur;
}
