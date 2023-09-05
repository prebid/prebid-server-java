package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RewardedVideoPartAdm {

    String adId;
    String id;
    Integer staticImageWidth;
    Integer staticImageHeight;
    String staticImageUrl;
    String clickUrl;

    public String toString() {
        return "<Creative adId=\"" + AdmUtils.getOrEmpty(adId) + "\" id=\"" + AdmUtils.getOrEmpty(id) + "\">"
                + "<CompanionAds>"
                + "<Companion width=\"" + AdmUtils.getOrEmpty(staticImageWidth)
                + "\" height=\"" + AdmUtils.getOrEmpty(staticImageHeight) + "\">"
                + "<StaticResource creativeType=\"image/png\">"
                + "<![CDATA[" + AdmUtils.getOrEmpty(staticImageUrl) + "]]></StaticResource>"
                + "<CompanionClickThrough><![CDATA[" + AdmUtils.getOrEmpty(clickUrl) + "]]></CompanionClickThrough>"
                + "</Companion>"
                + "</CompanionAds>"
                + "</Creative>";
    }

}
