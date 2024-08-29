package org.prebid.server.hooks.modules.response.correction.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Config {

    boolean enabled;

    @JsonProperty("app-video-html")
    AppVideoHtmlConfig appVideoHtmlConfig;
}
