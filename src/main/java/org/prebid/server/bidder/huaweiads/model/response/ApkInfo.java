package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ApkInfo {

    @JsonProperty("url")
    String url;

    @JsonProperty("fileSize")
    Long fileSize;

    @JsonProperty("sha256")
    String sha256;

    @JsonProperty("packageName")
    String packageName;

    @JsonProperty("secondUrl")
    String secondUrl;

    @JsonProperty("appName")
    String appName;

    @JsonProperty("versionName")
    String versionName;

    @JsonProperty("appDesc")
    String appDesc;

    @JsonProperty("appIcon")
    String appIcon;
}
