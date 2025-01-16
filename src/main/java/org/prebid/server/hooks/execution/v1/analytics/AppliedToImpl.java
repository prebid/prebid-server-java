package org.prebid.server.hooks.execution.v1.analytics;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.analytics.AppliedTo;

import java.util.List;

@Accessors(fluent = true)
@Builder
@Value
public class AppliedToImpl implements AppliedTo {

    List<String> impIds;

    List<String> bidders;

    boolean request;

    boolean response;

    List<String> bidIds;
}
