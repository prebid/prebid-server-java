package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VideoAdm {

    String adId;
    String creativeId;
    String adTitle;
    String duration;
    Integer height;
    Integer width;
    String resourceUrl;
    String clickUrl;
    String mime;
    String trackingEvents;
    String errorTracking;
    String dspImpTracking;
    String dspClickTracking;
    String rewardedVideoPart;

    public String toString() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"" + AdmUtils.getOrEmpty(adId) + "\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>" + AdmUtils.getOrEmpty(adTitle) + "</AdTitle>"
                + AdmUtils.getOrEmpty(errorTracking) + AdmUtils.getOrEmpty(dspImpTracking)
                + "<Creatives>"
                + "<Creative adId=\"" + AdmUtils.getOrEmpty(adId) + "\" id=\"" + AdmUtils.getOrEmpty(creativeId) + "\">"
                + "<Linear>"
                + "<Duration>" + AdmUtils.getOrEmpty(duration) + "</Duration>"
                + "<TrackingEvents>" + AdmUtils.getOrEmpty(trackingEvents) + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[" + AdmUtils.getOrEmpty(clickUrl) + "]]></ClickThrough>"
                + AdmUtils.getOrEmpty(dspClickTracking)
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"" + AdmUtils.getOrEmpty(mime)
                + "\" width=\"" + AdmUtils.getOrEmpty(width) + "\" "
                + "height=\"" + AdmUtils.getOrEmpty(height) + "\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[" + AdmUtils.getOrEmpty(resourceUrl) + "]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>" + AdmUtils.getOrEmpty(rewardedVideoPart)
                + "</Creatives>"
                + "</InLine></Ad></VAST>";
    }

}
