package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;
import org.prebid.server.activity.Activity;

@Value(staticConstructor = "of")
public class ExtTraceActivityInvocationResult implements ExtTraceActivityInfrastructure {

    String description = "Activity Infrastructure invocation result.";

    Activity activity;

    boolean allowed;
}
