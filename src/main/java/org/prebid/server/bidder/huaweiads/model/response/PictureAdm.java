package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PictureAdm {

    String imageTitle;
    String imageInfoUrl;
    String clickUrl;
    Integer width;
    Integer height;
    String dspClickTrackings;
    String dspImpTrackings;

    public String toString() {
        return "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle;"
                + " text-align: center; -webkit-text-size-adjust: none; }  "
                + "</style> "
                + "<span class=\"title-link advertiser_label\">" + AdmUtils.getOrEmpty(imageTitle) + "</span> "
                + "<a href='" + AdmUtils.getOrEmpty(clickUrl) + "' style=\"text-decoration:none\" onclick=sendGetReq()> "
                + "<img src='" + AdmUtils.getOrEmpty(imageInfoUrl)
                + "' width='" + AdmUtils.getOrEmpty(width) + "' height='" + AdmUtils.getOrEmpty(height) + "'/> "
                + "</a> "
                + AdmUtils.getOrEmpty(dspImpTrackings)
                + "<script type=\"text/javascript\">"
                + "var dspClickTrackings = [" + AdmUtils.getOrEmpty(dspClickTrackings) + "];"
                + "function sendGetReq() {"
                + "sendSomeGetReq(dspClickTrackings)"
                + "}"
                + "function sendOneGetReq(url) {"
                + "var req = new XMLHttpRequest();"
                + "req.open('GET', url, true);"
                + "req.send(null);"
                + "}"
                + "function sendSomeGetReq(urls) {for (var i = 0; i < urls.length; i++) {sendOneGetReq(urls[i]);}}"
                + "</script>";
    }

}
