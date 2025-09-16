package org.prebid.server.proto.openrtb.ext.request.kayzen;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpKayzen {

    String zone;

    String exchange;
}
