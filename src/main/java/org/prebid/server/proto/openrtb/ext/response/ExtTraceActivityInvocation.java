package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.prebid.server.activity.Activity;

@Value(staticConstructor = "of")
public class ExtTraceActivityInvocation implements ExtTraceActivityInfrastructure {

    String description;

    Activity activity;

    JsonNode activityInvocationPayload;
}
