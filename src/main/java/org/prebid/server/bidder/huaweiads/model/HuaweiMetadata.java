package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class HuaweiMetadata {
    private String title;
    private String description;
    private List<HuaweiImageInfo> imageInfo;
    private List<HuaweiIcon> icon;
    private String clickUrl;
    private String intent;
    private HuaweiVideoInfo videoInfo;
    private HuaweiApkInfo apkInfo;
    private int duration;
    private HuaweiMediaFile mediaFile;
}
