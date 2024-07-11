package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class ExplorationResult {

    String greenbidsId;

    Map<String, Boolean> keptInAuction;

    Boolean isExploration;
}
