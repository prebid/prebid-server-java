package org.prebid.server.proto.openrtb.ext.request.beop;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBeop {

    String pid;

    String nid;

    String ntpnid;
}
