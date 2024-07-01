package org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics;

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
