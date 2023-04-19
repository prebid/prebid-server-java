package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MetaData {

    String title;

    String description;

    List<ImageInfo> imageInfoList;

    List<Icon> iconList;

    String clickUrl;

    String intent;

    VideoInfo videoInfo;

    ApkInfo apkInfo;

    Long duration;

    MediaFile mediaFile;

}
