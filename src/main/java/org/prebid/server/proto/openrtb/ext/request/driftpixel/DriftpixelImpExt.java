package org.prebid.server.proto.openrtb.ext.request.driftpixel;

import lombok.Value;

@Value(staticConstructor = "of")
public class DriftpixelImpExt {

    String env;

    String pid;

}
