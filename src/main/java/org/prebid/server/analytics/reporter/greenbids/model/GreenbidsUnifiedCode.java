package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class GreenbidsUnifiedCode {

    String value;

    String source;
}
