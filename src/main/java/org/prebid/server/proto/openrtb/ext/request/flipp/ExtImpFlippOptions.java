package org.prebid.server.proto.openrtb.ext.request.flipp;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFlippOptions {

    Boolean startCompact;

    Boolean dwellExpand;

    String contentCode;
}
