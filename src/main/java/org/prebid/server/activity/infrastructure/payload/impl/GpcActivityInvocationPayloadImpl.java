package org.prebid.server.activity.infrastructure.payload.impl;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.infrastructure.payload.GpcActivityInvocationPayload;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class GpcActivityInvocationPayloadImpl implements GpcActivityInvocationPayload {

    String gpc;
}
