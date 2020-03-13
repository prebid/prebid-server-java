package org.prebid.server.proto.openrtb.ext.request.smartrtb;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSmartrtb {

    String pubId;

    String medId;

    String zoneId;

    Boolean forceBid;
}
