package org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class IncludeBiddersArguments implements ResultFunctionArguments {

    List<String> bidders;

    Integer seatNonBid;

    boolean ifSyncedId;

    String analyticsValue;
}
