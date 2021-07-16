package org.prebid.server.proto.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtAmpVideoResponse {

    ExtAmpVideoPrebid prebid;
}
