package org.prebid.server.proto.openrtb.ext.request.smartrtb;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestSmartrtb {

    String pubId;

    String zoneId;

    Boolean forceBid;
}
