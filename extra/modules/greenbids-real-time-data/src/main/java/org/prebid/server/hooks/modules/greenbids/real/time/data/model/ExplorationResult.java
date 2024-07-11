package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class ExplorationResult {

    String greenbidsId;

    Map<String, Boolean> keptInAuction;

    Boolean isExploration;
}
