package org.prebid.server.hooks.modules.pb.richmedia.filter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import jakarta.validation.constraints.NotBlank;

@Value(staticConstructor = "of")
public class PbRichMediaFilterProperties {

    @JsonProperty(value = "filter-mraid", required = true)
    Boolean filterMraid;

    @JsonProperty(value = "mraid-script-pattern", required = true)
    String mraidScriptPattern;

}
