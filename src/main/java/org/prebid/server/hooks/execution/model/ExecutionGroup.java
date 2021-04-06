package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExecutionGroup {

    Boolean synchronous;

    Long timeout;

    @JsonProperty("hook-sequence")
    List<HookId> hookSequence;
}
