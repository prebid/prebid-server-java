package org.rtb.vexing.model.request;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Sdk {

    String version;

    String source;

    String platform;
}
