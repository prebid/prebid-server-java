package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class App {

    @JsonProperty("version")
    String version;

    @JsonProperty("name")
    String name;

    @JsonProperty("pkgname")
    String pkgName;

    @JsonProperty("lang")
    String lang;

    @JsonProperty("country")
    String country;

}
