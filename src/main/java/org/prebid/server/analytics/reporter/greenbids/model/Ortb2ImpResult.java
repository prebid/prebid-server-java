package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class Ortb2ImpResult {

    Ortb2ImpExtResult ext;
}
