package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Ortb2ImpExtResult {

    ExplorationResult greenbids;

    String tid;
}
