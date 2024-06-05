package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class HookId {

    @JsonProperty("module-code")
    @JsonAlias("module_code")
    String moduleCode;

    @JsonProperty("hook-impl-code")
    @JsonAlias("hook_impl_code")
    String hookImplCode;
}
