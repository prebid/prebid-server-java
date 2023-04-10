package org.prebid.server.settings.model;

import lombok.Value;
import org.prebid.server.activity.ActivityInfrastructure;

@Value(staticConstructor = "of")
public class AccountInternalCache {

    ActivityInfrastructure activityInfrastructure;
}
