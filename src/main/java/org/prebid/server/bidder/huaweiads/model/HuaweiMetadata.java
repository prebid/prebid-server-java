package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HuaweiMetadata {

    String title;

    String description;

    List<HuaweiImageInfo> imageInfo;

    List<HuaweiIcon> icon;

    String clickUrl;

    String intent;

    HuaweiVideoInfo videoInfo;

    HuaweiApkInfo apkInfo;

    Integer duration;

    HuaweiMediaFile mediaFile;
}

