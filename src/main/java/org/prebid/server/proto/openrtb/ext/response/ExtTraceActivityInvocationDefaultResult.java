package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtTraceActivityInvocationDefaultResult implements ExtTraceActivityInfrastructure {

    String description = "Setting the default invocation result.";

    boolean allowByDefault;
}
