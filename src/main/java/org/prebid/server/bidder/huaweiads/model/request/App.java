package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class App {

    String version;

    String name;

    String pkgname;

    String lang;

    String country;

}
