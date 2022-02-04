package org.prebid.server.proto.openrtb.ext.request.adf;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdf {

    String mid;

    Integer inv;

    String mname;
}
