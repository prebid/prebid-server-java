package org.prebid.server.proto.openrtb.ext.request.smartrtb;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtRequestSmartrtb {

    String pubId;

    String zoneId;

    Boolean forceBid;
}
