package org.prebid.server.activity.infrastructure.privacy;

import lombok.Value;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

@Value(staticConstructor = "of")
public class TestPrivacyModule implements PrivacyModule {

    Result result;

    @Override
    public Result proceed(ActivityCallPayload activityCallPayload) {
        return result;
    }
}
