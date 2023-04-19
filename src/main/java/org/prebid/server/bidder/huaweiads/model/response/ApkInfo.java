package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class ApkInfo {

    String url;

    Long fileSize;

    String sha256;

    String packageName;

    String secondUrl;

    String appName;

    String versionName;

    String appDesc;

    String appIcon;

}
