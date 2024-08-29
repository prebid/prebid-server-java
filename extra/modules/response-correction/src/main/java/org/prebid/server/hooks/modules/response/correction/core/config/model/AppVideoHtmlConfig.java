package org.prebid.server.hooks.modules.response.correction.core.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AppVideoHtmlConfig {

    boolean enabled;

    @JsonProperty("excluded-bidders")
    List<String> excludedBidders;
}
