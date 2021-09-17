package org.prebid.server.hooks.modules.ortb2.blocking.v1.model.analytics;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.analytics.AppliedTo;

import java.util.List;

@Accessors(fluent = true)
@Value
@Builder
public class AppliedToImpl implements AppliedTo {

    List<String> impIds;

    List<String> bidders;

    boolean request;

    boolean response;

    List<String> bidIds;
}
