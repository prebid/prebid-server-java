package org.prebid.server.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Sdk {

    String version;

    String source;

    String platform;
}
