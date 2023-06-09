package org.prebid.server.proto.openrtb.ext.request.xeworks;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpXeworks {

    String env;

    String pid;
}
