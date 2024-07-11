package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Ort2ImpExtResult {

    ExplorationResult explorationResult;

    String tid;
}
