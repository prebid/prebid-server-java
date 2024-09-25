package org.prebid.server.hooks.modules.greenbids.real.time.data.model.result;

import lombok.Value;
import org.prebid.server.analytics.reporter.greenbids.model.ExplorationResult;

@Value(staticConstructor = "of")
public class Ortb2ImpExtResult {

    ExplorationResult greenbids;

    String tid;
}
