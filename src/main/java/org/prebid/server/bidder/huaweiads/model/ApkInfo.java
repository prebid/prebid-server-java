package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ApkInfo {
    private String url;
    private Integer fileSize;
    private String sha256;
    private String packageName;
    private String secondUrl;
    private String appName;
    private String versionName;
    private String appDesc;
    private String appIcon;
}
