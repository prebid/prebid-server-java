package org.prebid.server.proto.request.legacy;

import lombok.AllArgsConstructor;
import lombok.Value;

@Deprecated
@AllArgsConstructor(staticName = "of")
@Value
public class Sdk {

    String version;

    String source;

    String platform;
}
