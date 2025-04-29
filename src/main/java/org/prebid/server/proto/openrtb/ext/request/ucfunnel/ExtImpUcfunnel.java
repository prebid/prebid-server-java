package org.prebid.server.proto.openrtb.ext.request.ucfunnel;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpUcfunnel {

    String adunitid;

    String partnerid;
}
