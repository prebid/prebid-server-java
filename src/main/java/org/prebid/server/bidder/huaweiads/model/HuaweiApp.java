package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HuaweiApp {

    String version;

    String name;

    String pkgname;

    String lang;

    String country;
}

