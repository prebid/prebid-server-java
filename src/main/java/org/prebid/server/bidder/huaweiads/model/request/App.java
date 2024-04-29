package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class App {

    String version;

    String name;

    @JsonProperty("pkgname")
    String pkgName;

    String lang;

    String country;

}
