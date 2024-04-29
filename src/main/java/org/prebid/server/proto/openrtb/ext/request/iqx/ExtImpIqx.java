package org.prebid.server.proto.openrtb.ext.request.iqx;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpIqx {

    String env;

    String pid;
}
