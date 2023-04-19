package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class App {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String version;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String name;

    String pkgname;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String lang;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String country;

}
