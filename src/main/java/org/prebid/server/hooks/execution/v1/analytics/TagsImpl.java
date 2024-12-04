package org.prebid.server.hooks.execution.v1.analytics;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class TagsImpl implements Tags {

    List<Activity> activities;
}
