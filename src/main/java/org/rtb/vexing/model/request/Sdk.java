package org.rtb.vexing.model.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class Sdk {

    String version;

    String source;

    String platform;
}
