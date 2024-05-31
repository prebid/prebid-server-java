package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class HookId {

    @JsonAlias("module-code")
    String moduleCode;

    @JsonAlias("hook-impl-code")
    String hookImplCode;
}
