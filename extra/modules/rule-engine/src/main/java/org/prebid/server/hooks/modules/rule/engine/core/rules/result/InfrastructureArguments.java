package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;

import java.util.Map;

@Value(staticConstructor = "of")
public class InfrastructureArguments<C> {

    C context;

    Map<String, String> schemaFunctionResults;

    String analyticsKey;

    String ruleFired;

    String modelVersion;
}
