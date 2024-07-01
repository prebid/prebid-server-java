package org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Result;

import java.util.List;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ActivityImpl implements Activity {

    String name;

    String status;

    List<Result> results;
}
