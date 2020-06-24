package org.prebid.server.proto.openrtb.ext.request.kidoz;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpKidoz {

    String accessToken;

    String publisherID;
}
