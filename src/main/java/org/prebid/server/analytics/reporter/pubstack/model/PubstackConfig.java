package org.prebid.server.analytics.reporter.pubstack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@Value
@AllArgsConstructor(staticName = "of")
public class PubstackConfig {

    @JsonProperty("scopeId")
    String scopeId;

    String endpoint;

    Map<EventType, Boolean> features;
}
