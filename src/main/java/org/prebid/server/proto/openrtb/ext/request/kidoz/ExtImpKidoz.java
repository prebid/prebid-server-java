package org.prebid.server.proto.openrtb.ext.request.kidoz;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpKidoz {

    String accessToken;

    String publisherID;
}
