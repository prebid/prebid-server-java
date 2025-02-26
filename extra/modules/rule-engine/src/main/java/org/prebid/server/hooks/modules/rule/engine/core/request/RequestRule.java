package org.prebid.server.hooks.modules.rule.engine.core.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RequestRule {

    String name;

    String version;

    List<RuleSet> ruleSets;
}
