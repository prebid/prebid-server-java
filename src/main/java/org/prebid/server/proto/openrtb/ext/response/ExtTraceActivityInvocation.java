package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

@Value(staticConstructor = "of")
public class ExtTraceActivityInvocation implements ExtTraceActivityInfrastructure {

    String description;

    Activity activity;

    ActivityInvocationPayload activityInvocationPayload;
}
