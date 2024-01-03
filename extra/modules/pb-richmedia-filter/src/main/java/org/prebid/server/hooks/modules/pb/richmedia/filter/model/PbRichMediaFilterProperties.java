package org.prebid.server.hooks.modules.pb.richmedia.filter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class PbRichMediaFilterProperties {

    @JsonProperty("filter-mraid")
    Boolean filterMraid;

    @JsonProperty("mraid-script-pattern")
    String mraidScriptPattern;

}
