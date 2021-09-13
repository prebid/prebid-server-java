package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Metadata {
    private String title;
    private String description;
    private List<ImageInfo> imageInfo;
    private List<Icon> icon;
    private String clickUrl;
    private String intent;
    private VideoInfo videoInfo;
    private ApkInfo apkInfo;
    private Integer Duration;
    private MediaFile mediaFile;
}
