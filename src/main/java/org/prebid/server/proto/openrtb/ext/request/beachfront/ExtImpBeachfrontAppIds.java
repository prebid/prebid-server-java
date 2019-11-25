package org.prebid.server.proto.openrtb.ext.request.beachfront;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBeachfrontAppIds {

    String video;

    String banner;
}
