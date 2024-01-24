package org.prebid.server.proto.openrtb.ext.request.bematterfull;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBematterfull {

    String env;

    String pid;

}
