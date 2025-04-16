package org.prebid.server.hooks.modules.rule.engine.core.rules.result.arguments;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class InfrastructureArguments {

    Map<String, String> schemaFunctionResults;

    String analyticsKey;

    String ruleFired;

    String modelVersion;

    boolean validation;
}
