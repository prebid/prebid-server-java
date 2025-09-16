package org.prebid.server.analytics.reporter.log.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Value;

@Value(staticConstructor = "of")
public class LogEvent<T> {

    String type;

    @JsonUnwrapped
    T event;
}
