package org.prebid.server.proto.openrtb.ext.request.kayzen;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpKayzen {

    String zone;

    String exchange;
}
