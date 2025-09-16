package org.prebid.server.proto.openrtb.ext.request.bwx;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBwx {

    String env;

    String pid;
}
