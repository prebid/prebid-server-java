package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExecutionGroup {

    Long timeout;

    @JsonAlias("hook-sequence")
    List<HookId> hookSequence;
}
