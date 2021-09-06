package org.prebid.server.proto.openrtb.ext.request.gumgum;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpGumgumBanner {

    Long slot;

    Integer maxw;

    Integer maxh;
}
