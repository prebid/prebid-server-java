package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HuaweiApkInfo {

    String url;

    Integer fileSize;

    String sha256;

    String packageName;

    String secondUrl;

    String appName;

    String versionName;

    String appDesc;

    String appIcon;
}

