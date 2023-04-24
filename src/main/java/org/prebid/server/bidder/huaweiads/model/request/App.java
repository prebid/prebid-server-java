package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class App {

    String version;

    String name;

    String pkgname;

    String lang;

    String country;

}
