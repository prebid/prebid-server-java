package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class MetaData {

    @JsonProperty("title")
    String title;

    @JsonProperty("description")
    String description;

    @JsonProperty("imageInfo")
    List<ImageInfo> imageInfoList;

    @JsonProperty("icon")
    List<Icon> iconList;

    @JsonProperty("clickUrl")
    String clickUrl;

    @JsonProperty("intent")
    String intent;

    @JsonProperty("videoInfo")
    VideoInfo videoInfo;

    @JsonProperty("apkInfo")
    ApkInfo apkInfo;

    @JsonProperty("duration")
    Long duration;

    @JsonProperty("mediaFile")
    MediaFile mediaFile;
}
