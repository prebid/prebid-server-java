package org.prebid.server.hooks.v1.analytics;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

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
