package org.prebid.server.proto.openrtb.ext.request.lmkiviads;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLmKiviAds {

    String env;

    String pid;
}
