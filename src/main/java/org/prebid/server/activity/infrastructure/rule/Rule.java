package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;

public interface Rule {

    Result proceed(CompositeActivityInvocationPayload payload);

    enum Result {

        ALLOW,

        DISALLOW,

        ABSTAIN
    }
}
