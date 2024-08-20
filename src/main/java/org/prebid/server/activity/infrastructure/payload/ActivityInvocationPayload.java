package org.prebid.server.activity.infrastructure.payload;

public interface ActivityInvocationPayload {

    static ActivityInvocationPayloadBuilder builder() {
        return new ActivityInvocationPayloadBuilder();
    }
}
