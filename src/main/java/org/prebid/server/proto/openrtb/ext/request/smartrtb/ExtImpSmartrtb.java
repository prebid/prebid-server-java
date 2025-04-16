package org.prebid.server.proto.openrtb.ext.request.smartrtb;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSmartrtb {

    String pubId;

    String medId;

    String zoneId;

    Boolean forceBid;
}
