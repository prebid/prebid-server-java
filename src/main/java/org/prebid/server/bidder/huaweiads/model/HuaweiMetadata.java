package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
@Builder
public class HuaweiMetadata {

    String title;
    String description;
    List<HuaweiImageInfo> imageInfo;
    List<HuaweiIcon> icon;
    String clickUrl;
    String intent;
    HuaweiVideoInfo videoInfo;
    HuaweiApkInfo apkInfo;
    int duration;
    HuaweiMediaFile mediaFile;
}
