package org.prebid.server.proto.openrtb.ext.request.sovrn;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpSovrn {

    String tagid;

    Float bidfloor;
}
