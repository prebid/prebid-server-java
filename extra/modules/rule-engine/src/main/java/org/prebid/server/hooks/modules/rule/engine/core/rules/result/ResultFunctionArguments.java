package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ResultFunctionArguments {

    List<String> bidders;

    Integer seatNonBid;

    boolean ifSyncedId;

    String analyticsValue;
}
