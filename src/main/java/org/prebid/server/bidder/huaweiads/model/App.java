package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;

@Builder
public class App {
    private String version;
    private String name;
    private String pkgname;
    private String lang;
    private String country;
}
