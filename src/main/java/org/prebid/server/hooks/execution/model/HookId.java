package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class HookId {

    @JsonProperty("module-code")
    String moduleCode;

    @JsonProperty("hook-impl-code")
    String hookImplCode;
}
