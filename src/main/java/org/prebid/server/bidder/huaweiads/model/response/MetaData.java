package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class MetaData {

    String title;

    String description;

    @JsonProperty("imageInfo")
    List<ImageInfo> imageInfoList;

    @JsonProperty("icon")
    List<Icon> iconList;

    @JsonProperty("clickUrl")
    String clickUrl;

    String intent;

    @JsonProperty("videoInfo")
    VideoInfo videoInfo;

    @JsonProperty("apkInfo")
    ApkInfo apkInfo;

    Long duration;

    @JsonProperty("mediaFile")
    MediaFile mediaFile;

    String cta;

}
