package org.prebid.server.proto.openrtb.ext.request.adplayx;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdplayx {

    String apptoken;

    String placementid;
}
