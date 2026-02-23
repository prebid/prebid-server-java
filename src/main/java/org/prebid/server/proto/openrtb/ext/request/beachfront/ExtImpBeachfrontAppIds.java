package org.prebid.server.proto.openrtb.ext.request.beachfront;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBeachfrontAppIds {

    String video;

    String banner;
}
