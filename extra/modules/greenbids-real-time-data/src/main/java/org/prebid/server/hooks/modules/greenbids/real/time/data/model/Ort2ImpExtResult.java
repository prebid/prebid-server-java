package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Ort2ImpExtResult {

    ExplorationResult explorationResult;

    String tid;
}
