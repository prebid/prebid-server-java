package org.prebid.server.proto.openrtb.ext.request.teal;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTeal {

    String account;

    String placement;
}
