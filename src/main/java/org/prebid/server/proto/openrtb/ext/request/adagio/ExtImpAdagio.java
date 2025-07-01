package org.prebid.server.proto.openrtb.ext.request.adagio;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdagio {

    String organizationId;

    String placement;

    String pagetype;

    String category;
}
