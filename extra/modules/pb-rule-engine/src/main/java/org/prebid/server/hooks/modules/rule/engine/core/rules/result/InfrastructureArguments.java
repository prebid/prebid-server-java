package org.prebid.server.hooks.modules.rule.engine.core.rules.result;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value(staticConstructor = "of")
public class InfrastructureArguments<C> {

    C context;

    Map<String, String> schemaFunctionResults;

    Map<String, String> schemaFunctionMatches;

    String ruleFired;

    String analyticsKey;

    String modelVersion;
}
