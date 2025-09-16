package org.prebid.server.proto.openrtb.ext.request.gumgum;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGumgumBanner {

    Long slot;

    Integer maxw;

    Integer maxh;
}
