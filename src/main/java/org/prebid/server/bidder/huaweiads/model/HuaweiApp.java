package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class HuaweiApp {
    private String version;
    private String name;
    private String pkgname;
    private String lang;
    private String country;
}
