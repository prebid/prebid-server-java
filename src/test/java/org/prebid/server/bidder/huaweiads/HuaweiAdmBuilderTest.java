package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.TitleObject;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.VideoObject;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.huaweiads.model.AdsType;
import org.prebid.server.bidder.huaweiads.model.response.Content;
import org.prebid.server.bidder.huaweiads.model.response.HuaweiAdm;
import org.prebid.server.bidder.huaweiads.model.response.Icon;
import org.prebid.server.bidder.huaweiads.model.response.ImageInfo;
import org.prebid.server.bidder.huaweiads.model.response.MediaFile;
import org.prebid.server.bidder.huaweiads.model.response.MetaData;
import org.prebid.server.bidder.huaweiads.model.response.Monitor;
import org.prebid.server.bidder.huaweiads.model.response.VideoInfo;
import org.prebid.server.exception.PreBidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HuaweiAdmBuilderTest extends VertxTest {

    private final HuaweiAdmBuilder target = new HuaweiAdmBuilder(jacksonMapper);

    @Test
    public void buildBannerShouldFailWhenAdsTypeIsNative() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.NATIVE, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
    }

    @Test
    public void buildBannerShouldFailWhenAdsTypeIsRoll() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.ROLL, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
    }

    @Test
    public void buildBannerShouldFailWhenAdsTypeIsRewarded() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.REWARDED, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
    }

    @Test
    public void buildBannerShouldFailWhenAdsTypeIsAudio() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.AUDIO, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("openrtb banner should correspond to huaweiads adtype: banner or interstitial");
    }

    @Test
    public void buildBannerShouldFailWhenCreativeTypeIsUnknown() {
        // given
        final Content content = Content.builder().creativeType(200).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("no banner support creativetype");
    }

    @Test
    public void buildBannerShouldFailWhenContentMetadataIsEmpty() {
        // given
        final Content content = Content.builder().metaData(null).creativeType(9).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData is empty");
    }

    @Test
    public void buildBannerShouldFailBuildingVideoCreativeWhenInteractionTypeIsAppPromotionAndIntentIsAbsent() {
        // given
        final MetaData metadata = MetaData.builder().intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(3).creativeType(9).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.INTERSTITIAL, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent in huaweiads response is empty "
                        + "when interactiontype is appPromotion");
    }

    @Test
    public void buildBannerShouldFailBuildingVideoTextCreativeWhenIntentAndClickUrlAreAbsent() {
        // given
        final MetaData metadata = MetaData.builder().intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(2).creativeType(6).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.INTERSTITIAL, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent and content.MetaData.ClickUrl in huaweiads response is empty");
    }

    @Test
    public void buildBannerShouldFailBuildingVideoWithPicturesTextCreativeWhenVideoInfoIsAbsent() {
        // given
        final MetaData metadata = MetaData.builder().clickUrl("clickUrl").videoInfo(null).build();
        final Content content = Content.builder().metaData(metadata).creativeType(11).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.INTERSTITIAL, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo is mandatory for video impression");
    }

    @Test
    public void buildBannerShouldFailBuildingVideoWithPicturesTextCreativeWhenVideoInfoHasEmptyUrl() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder().videoDownloadUrl("").build();
        final MetaData metadata = MetaData.builder().clickUrl("clickUrl").videoInfo(videoInfo).build();
        final Content content = Content.builder().metaData(metadata).creativeType(11).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
    }

    @Test
    public void buildBannerShouldFailBuildingVideoCreativeWhenVideoInfoHasEmptyFormat() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(100L)
                .videoDownloadUrl("videoUrl")
                .width(0)
                .height(0)
                .build();
        final MetaData metadata = MetaData.builder().clickUrl("clickUrl").videoInfo(videoInfo).build();
        final Content content = Content.builder().metaData(metadata).creativeType(9).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: cannot get video width, height");
    }

    @Test
    public void buildBannerShouldFailBuildingVideoCreativeWhenVideoInfoHasEmptyDuration() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(null)
                .videoDownloadUrl("videoUrl")
                .width(0)
                .height(0)
                .build();
        final MetaData metadata = MetaData.builder().clickUrl("clickUrl").videoInfo(videoInfo).build();
        final Content content = Content.builder().metaData(metadata).creativeType(9).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo duration is empty");
    }

    @Test
    public void buildBannerShouldBuildVideoCreativeWithClickUrlAndEmptyTitleAndUserCloseMonitorEvent() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .width(200)
                .height(200)
                .build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .videoInfo(videoInfo)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("userClose", List.of("userCloseUrl1", "userCloseUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .creativeType(9)
                .build();

        // when
        final HuaweiAdm actual = target.buildBanner(AdsType.BANNER, content);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"skip\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\"closeLinear\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\"skip\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "<Tracking event=\"closeLinear\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldBuildVideoCreativeWithDecodedIntentAsClickUrlAndTitleAndPlayStartMonitorEvent() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .width(200)
                .height(200)
                .build();
        final MetaData metadata = MetaData.builder()
                .title("title%20title")
                .clickUrl("clickUrl")
                .intent("intent%20intent")
                .videoInfo(videoInfo)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .interactionType(3)
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("playStart", List.of("playStartUrl1", "playStartUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .creativeType(9)
                .build();

        // when
        final HuaweiAdm actual = target.buildBanner(AdsType.INTERSTITIAL, content);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>title title</AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"start\"><![CDATA[playStartUrl1]]></Tracking>"
                + "<Tracking event=\"start\"><![CDATA[playStartUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[intent intent]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldBuildVideoCreativeWithMinimalPossibleAdm() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .width(200)
                .height(200)
                .build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .videoInfo(videoInfo)
                .build();
        final Content content = Content.builder()
                .metaData(metadata)
                .creativeType(9)
                .build();

        // when
        final HuaweiAdm actual = target.buildBanner(AdsType.INTERSTITIAL, content);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Creatives>"
                + "<Creative adId=\"\" id=\"\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldFailBuildingTextCreativeWhenMetadataIsEmpty() {
        // given
        final Content content = Content.builder().metaData(null).creativeType(1).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData is empty");
    }

    @Test
    public void buildBannerShouldFailBuildingBigPictureCreativeWhenImageInfoListIsNull() {
        // given
        final MetaData metadata = MetaData.builder().imageInfoList(null).build();
        final Content content = Content.builder().metaData(metadata).creativeType(2).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.ImageInfo is empty");
    }

    @Test
    public void buildBannerShouldFailBuildingBigPicture2CreativeWhenImageInfoListIsEmpty() {
        // given
        final MetaData metadata = MetaData.builder().imageInfoList(List.of()).build();
        final Content content = Content.builder().metaData(metadata).creativeType(3).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.ImageInfo is empty");
    }

    @Test
    public void buildBannerShouldFailBuildingSmallPictureCreativeWhenImageInfoHasEmptyFormat() {
        // given
        final ImageInfo imageInfo = ImageInfo.builder().width(0).build();
        final MetaData metadata = MetaData.builder().imageInfoList(List.of(imageInfo)).build();
        final Content content = Content.builder().metaData(metadata).creativeType(7).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.ImageInfo doesn't have width and/or height");
    }

    @Test
    public void buildBannerShouldFailBuilding3PicturesTextCreativeWhenInteractionTypeIsAppPromotionAndIntentIsAbsent() {
        // given
        final ImageInfo imageInfo = ImageInfo.builder().width(100).height(100).build();
        final MetaData metadata = MetaData.builder().imageInfoList(List.of(imageInfo)).intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(3).creativeType(8).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.BANNER, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent in huaweiads response is empty "
                        + "when interactiontype is appPromotion");
    }

    @Test
    public void buildBannerShouldFailBuildingIconTextCreativeWhenIntentAndClickUrlAreAbsent() {
        // given
        final ImageInfo imageInfo = ImageInfo.builder().width(100).height(100).build();
        final MetaData metadata = MetaData.builder().imageInfoList(List.of(imageInfo)).build();
        final Content content = Content.builder().metaData(metadata).interactionType(10).creativeType(10).build();

        // when & then
        assertThatThrownBy(() -> target.buildBanner(AdsType.INTERSTITIAL, content))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent and content.MetaData.ClickUrl in huaweiads response is empty");
    }

    @Test
    public void buildBannerShouldBuildGifCreativeWithClickUrlAndEmptyTitle() {
        // given
        final ImageInfo imageInfo1 = ImageInfo.builder().url("imageInfoUrl").width(100).height(100).build();
        final ImageInfo imageInfo2 = ImageInfo.builder().width(200).height(200).build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .imageInfoList(List.of(imageInfo1, imageInfo2))
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("userClose", List.of("userCloseUrl1", "userCloseUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("imp", List.of("impUrl3")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2")),
                        Monitor.of("click", List.of("clickUrl3", "clickUrl4"))))
                .creativeType(4)
                .build();

        // when
        final HuaweiAdm actual = target.buildBanner(AdsType.BANNER, content);

        // then
        final String expectedAdm = "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle;"
                + " text-align: center; -webkit-text-size-adjust: none; }  "
                + "</style> "
                + "<span class=\"title-link advertiser_label\"></span> "
                + "<a href='clickUrl' style=\"text-decoration:none\" onclick=sendGetReq()> "
                + "<img src='imageInfoUrl"
                + "' width='100' height='100'/> "
                + "</a> "
                + "<img height=\"1\" width=\"1\" src='impUrl1' >  "
                + "<img height=\"1\" width=\"1\" src='impUrl2' >  "
                + "<script type=\"text/javascript\">"
                + "var dspClickTrackings = [\"clickUrl1\",\"clickUrl2\"];"
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

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 100, 100));
    }

    @Test
    public void buildBannerShouldBuildGifCreativeWithDecodedIntentAsClickUrlAndTitle() {
        // given
        final ImageInfo imageInfo = ImageInfo.builder().url("imageInfoUrl").width(100).height(100).build();
        final MetaData metadata = MetaData.builder()
                .title("title%20title")
                .clickUrl("clickUrl")
                .intent("intent%20intent")
                .imageInfoList(List.of(imageInfo))
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .interactionType(3)
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .creativeType(4)
                .build();

        // when
        final HuaweiAdm actual = target.buildBanner(AdsType.INTERSTITIAL, content);

        // then
        final String expectedAdm = "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle;"
                + " text-align: center; -webkit-text-size-adjust: none; }  "
                + "</style> "
                + "<span class=\"title-link advertiser_label\">title title</span> "
                + "<a href='intent intent' style=\"text-decoration:none\" onclick=sendGetReq()> "
                + "<img src='imageInfoUrl"
                + "' width='100' height='100'/> "
                + "</a> "
                + "<img height=\"1\" width=\"1\" src='impUrl1' >  "
                + "<img height=\"1\" width=\"1\" src='impUrl2' >  "
                + "<script type=\"text/javascript\">"
                + "var dspClickTrackings = [\"clickUrl1\",\"clickUrl2\"];"
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

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 100, 100));
    }

    @Test
    public void buildBannerShouldBuildGifCreativeWithMinimalPossibleAdm() {
        // given
        final ImageInfo imageInfo = ImageInfo.builder().width(100).height(100).build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .imageInfoList(List.of(imageInfo))
                .build();
        final Content content = Content.builder()
                .metaData(metadata)
                .creativeType(4)
                .build();

        // when
        final HuaweiAdm actual = target.buildBanner(AdsType.INTERSTITIAL, content);

        // then
        final String expectedAdm = "<style> html, body  "
                + "{ margin: 0; padding: 0; width: 100%; height: 100%; vertical-align: middle; }  "
                + "html  "
                + "{ display: table; }  "
                + "body { display: table-cell; vertical-align: middle;"
                + " text-align: center; -webkit-text-size-adjust: none; }  "
                + "</style> "
                + "<span class=\"title-link advertiser_label\"></span> "
                + "<a href='clickUrl' style=\"text-decoration:none\" onclick=sendGetReq()> "
                + "<img src='' width='100' height='100'/> "
                + "</a> "
                + "<script type=\"text/javascript\">"
                + "var dspClickTrackings = [];"
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

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 100, 100));
    }

    @Test
    public void buildVideoShouldFailWhenAdsTypeIsAudio() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.AUDIO, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("openrtb video should correspond to huaweiads adtype: "
                        + "banner, interstitial, roll, rewarded or native");
    }

    @Test
    public void buildVideoShouldFailWhenMetadataIsEmpty() {
        // given
        final Content content = Content.builder().metaData(null).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.BANNER, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData is empty");
    }

    @Test
    public void buildVideoShouldFailWhenInteractionTypeIsAppPromotionAndIntentIsAbsent() {
        // given
        final MetaData metadata = MetaData.builder().intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(3).creativeType(9).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.INTERSTITIAL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent in huaweiads response is empty "
                        + "when interactiontype is appPromotion");
    }

    @Test
    public void buildVideoShouldFailWhenIntentAndClickUrlAreAbsent() {
        // given
        final MetaData metadata = MetaData.builder().intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(2).creativeType(6).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.INTERSTITIAL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent and content.MetaData.ClickUrl in huaweiads response is empty");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsRollAndMediaFileIsAbsent() {
        // given
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").mediaFile(null).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.ROLL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.MediaFile is mandatory for roll video impression");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsRollAndMediaFileHasEmptyUrl() {
        // given
        final MediaFile mediaFile = MediaFile.of(null, null, null, null, "", null);
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").mediaFile(mediaFile).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.ROLL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: Content.MetaData.MediaFile.Url is empty");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsRollAndMetadataDurationIsAbsent() {
        // given
        final MediaFile mediaFile = MediaFile.of(null, null, null, null, "url", null);
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").mediaFile(mediaFile).duration(null).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.ROLL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo duration is empty");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsRollAndMediaFileHasEmptyFormat() {
        // given
        final MediaFile mediaFile = MediaFile.of(null, null, null, null, "url", null);
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").mediaFile(mediaFile).duration(1000L).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.ROLL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: cannot get video width, height");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsBannerAndVideoInfo() {
        // given
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").videoInfo(null).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.BANNER, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo is mandatory for video impression");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsInterstitialAndMediaFileHasEmptyUrl() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder().videoDownloadUrl("").build();
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").videoInfo(videoInfo).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.INTERSTITIAL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsRewardedAndMetadataVideoInfoDurationIsAbsent() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder().videoDuration(null).videoDownloadUrl("url").build();
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").videoInfo(videoInfo).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.REWARDED, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo duration is empty");
    }

    @Test
    public void buildVideoShouldFailWhenAdsIsBannerAndVideoInfoHasEmptyFormatAndVideoIsEmpty() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder().videoDuration(1000L).videoDownloadUrl("url").build();
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl").videoInfo(videoInfo).build();
        final Content content = Content.builder().metaData(metaData).build();

        // when & then
        assertThatThrownBy(() -> target.buildVideo(AdsType.BANNER, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: cannot get video width, height");
    }

    @Test
    public void buildVideoShouldBuildRollVideoWithClickUrlAndEmptyTitleAndUserCloseEventAndMediaFileMime() {
        // given
        final MediaFile mediaFile = MediaFile.of(
                "mime",
                200,
                200,
                null,
                "mediaFileUrl",
                null);
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .mediaFile(mediaFile)
                .duration(1000L)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("userClose", List.of("userCloseUrl1", "userCloseUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .build();

        // when
        final HuaweiAdm actual = target.buildVideo(AdsType.ROLL, content, null);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"skip\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\"closeLinear\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\"skip\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "<Tracking event=\"closeLinear\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"mime\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[mediaFileUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldBuildRollVideoWithDecodedIntentAsClickUrlAndTitleAndPlayResumeEventAndMimeIsAbsent() {
        // given
        final MediaFile mediaFile = MediaFile.of(
                "",
                200,
                200,
                null,
                "mediaFileUrl",
                null);
        final MetaData metadata = MetaData.builder()
                .title("title%20title")
                .clickUrl("clickUrl")
                .intent("intent%20intent")
                .mediaFile(mediaFile)
                .duration(1000L)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .interactionType(3)
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("playResume", List.of("playResumeUrl1", "playResumeUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .build();

        // when
        final HuaweiAdm actual = target.buildVideo(AdsType.ROLL, content, null);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>title title</AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"resume\"><![CDATA[playResumeUrl1]]></Tracking>"
                + "<Tracking event=\"resume\"><![CDATA[playResumeUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[intent intent]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[mediaFileUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildVideoShouldBuildBannerVideoWithClickUrlAndEmptyTitleAndUserCloseEventAndFormatIsTakenFromVideo() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .videoInfo(videoInfo)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("userClose", List.of("userCloseUrl1", "userCloseUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .build();
        final Video video = Video.builder().w(200).h(200).build();

        // when
        final HuaweiAdm actual = target.buildVideo(AdsType.BANNER, content, video);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"skip\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\"closeLinear\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\"skip\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "<Tracking event=\"closeLinear\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldBuildInterstitialVideoWithDecodedIntentAsClickUrlAndTitleAndPlayEndEvent() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .width(200)
                .height(200)
                .build();
        final MetaData metadata = MetaData.builder()
                .title("title%20title")
                .clickUrl("clickUrl")
                .intent("intent%20intent")
                .videoInfo(videoInfo)
                .duration(2000L)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .interactionType(3)
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("playEnd", List.of("playUrl1", "playUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .build();

        // when
        final HuaweiAdm actual = target.buildVideo(AdsType.INTERSTITIAL, content, null);

        // then
        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>title title</AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"complete\"><![CDATA[playUrl1]]></Tracking>"
                + "<Tracking event=\"complete\"><![CDATA[playUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[intent intent]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldBuildRewardedVideoWithIconListAndSoundOffEvent() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .width(200)
                .height(200)
                .build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .videoInfo(videoInfo)
                .iconList(List.of(Icon.builder().url("iconUrl").width(300).height(300).build()))
                .imageInfoList(List.of(ImageInfo.builder().url("imageInfoUrl").width(400).height(400).build()))
                .duration(2000L)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("soundClickOff", List.of("soundClickUrl1", "soundClickUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .build();
        final Video video = Video.builder().w(100).h(100).build();

        // when
        final HuaweiAdm actual = target.buildVideo(AdsType.REWARDED, content, video);

        // then
        final String expectedRewardedPart = "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<CompanionAds>"
                + "<Companion width=\"300\" height=\"300\">"
                + "<StaticResource creativeType=\"image/png\">"
                + "<![CDATA[iconUrl]]></StaticResource>"
                + "<CompanionClickThrough><![CDATA[clickUrl]]></CompanionClickThrough>"
                + "</Companion>"
                + "</CompanionAds>"
                + "</Creative>";

        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"mute\"><![CDATA[soundClickUrl1]]></Tracking>"
                + "<Tracking event=\"mute\"><![CDATA[soundClickUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>" + expectedRewardedPart
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildBannerShouldBuildRewardedVideoWithImageInfoListAndSoundOnEvent() {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDuration(1000L)
                .videoDownloadUrl("videoUrl")
                .width(200)
                .height(200)
                .build();
        final MetaData metadata = MetaData.builder()
                .clickUrl("clickUrl")
                .videoInfo(videoInfo)
                .iconList(List.of())
                .imageInfoList(List.of(ImageInfo.builder().url("imageInfoUrl").width(400).height(400).build()))
                .duration(2000L)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .metaData(metadata)
                .monitorList(List.of(
                        Monitor.of("soundClickOn", List.of("soundClickUrl1", "soundClickUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2")),
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2"))))
                .build();
        final Video video = Video.builder().w(100).h(100).build();

        // when
        final HuaweiAdm actual = target.buildVideo(AdsType.REWARDED, content, video);

        // then
        final String expectedRewardedPart = "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<CompanionAds>"
                + "<Companion width=\"400\" height=\"400\">"
                + "<StaticResource creativeType=\"image/png\">"
                + "<![CDATA[imageInfoUrl]]></StaticResource>"
                + "<CompanionClickThrough><![CDATA[clickUrl]]></CompanionClickThrough>"
                + "</Companion>"
                + "</CompanionAds>"
                + "</Creative>";

        final String expectedAdm = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<VAST version=\"3.0\">"
                + "<Ad id=\"contentId\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl1]]></Impression><Impression><![CDATA[impUrl2]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\"contentId\" id=\"contentId\">"
                + "<Linear>"
                + "<Duration>00:00:01.000</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\"unmute\"><![CDATA[soundClickUrl1]]></Tracking>"
                + "<Tracking event=\"unmute\"><![CDATA[soundClickUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl1]]></ClickTracking>"
                + "<ClickTracking><![CDATA[clickUrl2]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\"progressive\" type=\"video/mp4\" "
                + "width=\"200\" height=\"200\" scalable=\"true\" maintainAspectRatio=\"true\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>" + expectedRewardedPart
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 200, 200));
    }

    @Test
    public void buildNativeShouldFailWhenAdsTypeIsBanner() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.BANNER, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for Native ad: huaweiads response is not a native ad");
    }

    @Test
    public void buildNativeShouldFailWhenAdsTypeIsAudio() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.AUDIO, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for Native ad: huaweiads response is not a native ad");
    }

    @Test
    public void buildNativeShouldFailWhenAdsTypeIsInterstitial() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.INTERSTITIAL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for Native ad: huaweiads response is not a native ad");
    }

    @Test
    public void buildNativeShouldFailWhenAdsTypeIsRewarded() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.REWARDED, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for Native ad: huaweiads response is not a native ad");
    }

    @Test
    public void buildNativeShouldFailWhenAdsTypeIsRoll() {
        // given
        final Content content = Content.builder().build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.ROLL, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for Native ad: huaweiads response is not a native ad");
    }

    @Test
    public void buildNativeShouldFailWhenMetadataIsAbsent() {
        // given
        final Content content = Content.builder().metaData(null).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData is empty");
    }

    @Test
    public void buildNativeShouldFailWhenInteractionTypeIsAppPromotionAndIntentIsAbsent() {
        // given
        final MetaData metadata = MetaData.builder().intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(3).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent in huaweiads response is empty "
                        + "when interactiontype is appPromotion");
    }

    @Test
    public void buildNativeShouldFailWhenIntentAndClickUrlAreAbsent() {
        // given
        final MetaData metadata = MetaData.builder().intent("").build();
        final Content content = Content.builder().metaData(metadata).interactionType(2).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, null))
                .isInstanceOf(PreBidException.class)
                .hasMessage("content.MetaData.Intent and content.MetaData.ClickUrl in huaweiads response is empty");
    }

    @Test
    public void buildNativeShouldFailWhenNativeHasEmptyRequest() {
        // given
        final Content content = Content.builder().metaData(MetaData.builder().clickUrl("clickUrl").build()).build();
        final Native xNative = Native.builder().request("").build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, xNative))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract openrtb native failed: imp.Native.Request is empty");
    }

    @Test
    public void buildNativeShouldFailWhenNativeHasRequestThatCanNotBeParsed() {
        // given
        final Content content = Content.builder().metaData(MetaData.builder().clickUrl("clickUrl").build()).build();
        final Native xNative = Native.builder().request("invalid_request").build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, xNative))
                .isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Unrecognized token");
    }

    @Test
    public void buildNativeShouldBuildNativeWithClickUrlWithoutAssetsWhenAssetsAreEmptyAndVersionIsAbsent() {
        // given
        final MetaData metaData = MetaData.builder().clickUrl("clickUrl%20clickUrl").build();
        final Content content = Content.builder().metaData(metaData).build();
        final Native xNative = Native.builder().request("{\"ver\":null}").build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedAdm = "{"
                + "\"ver\":\"1.1\","
                + "\"assets\":[],"
                + "\"link\":{\"url\":\"clickUrl%20clickUrl\",\"clicktrackers\":[]},"
                + "\"eventtrackers\":[]"
                + "}";
        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, null, null));
    }

    @Test
    public void buildNativeShouldBuildNativeWithDecodedIntentWithoutAssetsWhenAssetsAreEmptyAndVersionIsPresent() {
        // given
        final MetaData metaData = MetaData.builder().intent("intent%20intent").build();
        final Content content = Content.builder().metaData(metaData).interactionType(3).build();
        final Native xNative = Native.builder().request("{\"ver\":\"2.0\"}").build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedAdm = "{"
                + "\"ver\":\"2.0\","
                + "\"assets\":[],"
                + "\"link\":{\"url\":\"intent intent\",\"clicktrackers\":[]},"
                + "\"eventtrackers\":[]"
                + "}";
        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, null, null));
    }

    @Test
    public void buildNativeShouldBuildNativeWithClickAndImpTrackings() {
        // given
        final MetaData metaData = MetaData.builder().intent("intent%20intent").build();
        final Content content = Content.builder()
                .monitorList(List.of(
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2")),
                        Monitor.of("imp", List.of("impUrl3", "impUrl4")),
                        Monitor.of("click", List.of("clickUrl3"))))
                .metaData(metaData)
                .build();
        final Native xNative = Native.builder().request("{}").build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedAdm = "{"
                + "\"ver\":\"1.1\","
                + "\"assets\":[],"
                + "\"link\":{\"url\":\"intent intent\",\"clicktrackers\":[\"clickUrl1\",\"clickUrl2\",\"clickUrl3\"]},"
                + "\"eventtrackers\":["
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl1\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl2\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl3\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl4\"}]"
                + "}";
        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, null, null));
    }

    @Test
    public void buildNativeShouldBuildNativeWithTitleWhenNativeHasTitleAsset() throws JsonProcessingException {
        // given
        final MetaData metaData = MetaData.builder().title("title").clickUrl("clickUrl").build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().id(12).title(TitleObject.builder().build()).build()))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedAdm = "{"
                + "\"ver\":\"1.1\","
                + "\"assets\":[{\"id\":12,\"title\":{\"text\":\"title\",\"len\":5}}],"
                + "\"link\":{\"url\":\"clickUrl\",\"clicktrackers\":[]},"
                + "\"eventtrackers\":[]"
                + "}";
        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, null, null));
    }

    @Test
    public void buildNativeShouldFailBuildNativeWithVideoWhenVideoInfoIsAbsent() throws JsonProcessingException {
        // given
        final MetaData metaData = MetaData.builder().videoInfo(null).clickUrl("clickUrl").build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().id(12).video(VideoObject.builder().build()).build()))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, xNative))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo is mandatory for video impression");
    }

    @Test
    public void buildNativeShouldFailBuildNativeWithVideoWhenVideoInfoUrlIsAbsent() throws JsonProcessingException {
        // given
        final VideoInfo videoInfo = VideoInfo.builder().videoDownloadUrl("").build();
        final MetaData metaData = MetaData.builder().videoInfo(videoInfo).clickUrl("clickUrl").build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().id(12).video(VideoObject.builder().build()).build()))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, xNative))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: content.MetaData.VideoInfo.VideoDownloadUrl is empty");
    }

    @Test
    public void buildNativeShouldFailBuildNativeWithVideoWhenVideoInfoDurationIsAbsent()
            throws JsonProcessingException {

        // given
        final VideoInfo videoInfo = VideoInfo.builder().videoDownloadUrl("url").videoDuration(null).build();
        final MetaData metaData = MetaData.builder().videoInfo(videoInfo).clickUrl("clickUrl").build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().id(12).video(VideoObject.builder().build()).build()))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, xNative))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Content.MetaData.VideoInfo duration is empty");
    }

    @Test
    public void buildNativeShouldFailBuildNativeWithVideoWhenVideoInfoFormatIsAbsent() throws JsonProcessingException {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDownloadUrl("url")
                .videoDuration(100L)
                .width(0)
                .height(null)
                .build();
        final MetaData metaData = MetaData.builder().videoInfo(videoInfo).clickUrl("clickUrl").build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().id(12).video(VideoObject.builder().build()).build()))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when & then
        assertThatThrownBy(() -> target.buildNative(AdsType.NATIVE, content, xNative))
                .isInstanceOf(PreBidException.class)
                .hasMessage("extract Adm for video failed: cannot get video width, height");
    }

    @Test
    public void buildNativeShouldBuildNativeWithVideoWhenMonitorListIsPresent() throws JsonProcessingException {
        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDownloadUrl("videoUrl")
                .videoDuration(100L)
                .width(100)
                .height(100)
                .build();
        final MetaData metaData = MetaData.builder().videoInfo(videoInfo).clickUrl("clickUrl").build();
        final Content content = Content.builder()
                .contentId("contentId")
                .monitorList(List.of(
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2")),
                        Monitor.of("imp", List.of("impUrl3", "impUrl4")),
                        Monitor.of("click", List.of("clickUrl3")),
                        Monitor.of("userClose", List.of("userCloseUrl1", "userCloseUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2"))
                ))
                .metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(Asset.builder().id(12).video(VideoObject.builder().build()).build()))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedVideoAssetAdm = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>"
                + "<VAST version=\\\"3.0\\\">"
                + "<Ad id=\\\"contentId\\\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle></AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl3]]></Impression><Impression><![CDATA[impUrl4]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\\\"contentId\\\" id=\\\"contentId\\\">"
                + "<Linear>"
                + "<Duration>00:00:00.100</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\\\"skip\\\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\\\"closeLinear\\\"><![CDATA[userCloseUrl1]]></Tracking>"
                + "<Tracking event=\\\"skip\\\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "<Tracking event=\\\"closeLinear\\\"><![CDATA[userCloseUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl3]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\\\"progressive\\\" type=\\\"video/mp4\\\" "
                + "width=\\\"100\\\" height=\\\"100\\\" scalable=\\\"true\\\" maintainAspectRatio=\\\"true\\\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";

        final String expectedAdm = "{"
                + "\"ver\":\"1.1\","
                + "\"assets\":[{\"id\":12,\"video\":{\"vasttag\":\"" + expectedVideoAssetAdm + "\"}}],"
                + "\"link\":{\"url\":\"clickUrl\",\"clicktrackers\":[\"clickUrl1\",\"clickUrl2\",\"clickUrl3\"]},"
                + "\"eventtrackers\":["
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl1\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl2\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl3\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl4\"}]"
                + "}";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 100, 100));
    }

    @Test
    public void buildNativeShouldBuildNativeWithImagesWhenImageAreFromIconAndImageInfoLists()
            throws JsonProcessingException {

        // given
        final MetaData metaData = MetaData.builder()
                .iconList(List.of(
                        Icon.builder().url("iconUrl").build(),
                        Icon.builder().url("").width(100).height(100).build()))
                .imageInfoList(List.of(
                        ImageInfo.builder().url("imageInfoUrl").width(200).height(0).build(),
                        ImageInfo.builder().url(null).width(200).height(200).build()))
                .clickUrl("clickUrl")
                .build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().id(12).img(ImageObject.builder().type(1).build()).build(),
                        Asset.builder().id(13).img(ImageObject.builder().type(1).build()).build(),
                        Asset.builder().id(24).img(ImageObject.builder().type(2).build()).build(),
                        Asset.builder().id(25).img(ImageObject.builder().type(3).build()).build()
                ))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedAdm = "{"
                + "\"ver\":\"1.1\","
                + "\"assets\":["
                + "{\"id\":12,\"img\":{\"type\":1,\"url\":\"iconUrl\"}},"
                + "{\"id\":13,\"img\":{\"type\":1,\"url\":\"\",\"w\":100,\"h\":100}},"
                + "{\"id\":24,\"img\":{\"type\":2,\"url\":\"imageInfoUrl\",\"w\":200,\"h\":0}},"
                + "{\"id\":25,\"img\":{\"type\":3,\"w\":200,\"h\":200}}"
                + "],"
                + "\"link\":{\"url\":\"clickUrl\",\"clicktrackers\":[]},"
                + "\"eventtrackers\":[]"
                + "}";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 100, 100));
    }

    @Test
    public void buildNativeShouldBuildNativeWithDataAssets() throws JsonProcessingException {
        // given
        final MetaData metaData = MetaData.builder()
                .title("title%20title")
                .clickUrl("clickUrl")
                .build();
        final Content content = Content.builder().metaData(metaData).build();
        final Request nativeRequest = Request.builder()
                .assets(List.of(
                        Asset.builder().id(12).data(DataObject.builder().type(2).build()).build(),
                        Asset.builder().id(13).data(DataObject.builder().type(10).build()).build(),
                        Asset.builder().id(14).data(DataObject.builder().type(3).build()).build()
                ))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedAdm = "{"
                + "\"ver\":\"1.1\","
                + "\"assets\":["
                + "{\"id\":12,\"data\":{\"value\":\"title title\",\"label\":\"desc\"}},"
                + "{\"id\":13,\"data\":{\"value\":\"title title\",\"label\":\"desc\"}},"
                + "{\"id\":14,\"data\":{\"value\":\"\",\"label\":\"\"}}"
                + "],"
                + "\"link\":{\"url\":\"clickUrl\",\"clicktrackers\":[]},"
                + "\"eventtrackers\":[]"
                + "}";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, null, null));
    }

    @Test
    public void buildNativeShouldBuildNativeWithTitleVideoImagesAndDataAssetsWhenMonitorListIsPresent()
            throws JsonProcessingException {

        // given
        final VideoInfo videoInfo = VideoInfo.builder()
                .videoDownloadUrl("videoUrl")
                .videoDuration(100L)
                .width(300)
                .height(300)
                .build();
        final MetaData metaData = MetaData.builder()
                .title("title%20title")
                .clickUrl("clickUrl")
                .iconList(List.of(
                        Icon.builder().url("iconUrl").build(),
                        Icon.builder().url("").width(100).height(100).build()))
                .imageInfoList(List.of(
                        ImageInfo.builder().url("imageInfoUrl").width(200).height(0).build(),
                        ImageInfo.builder().url(null).width(200).height(200).build()))
                .videoInfo(videoInfo)
                .build();
        final Content content = Content.builder()
                .contentId("contentId")
                .monitorList(List.of(
                        Monitor.of("imp", List.of("impUrl1", "impUrl2")),
                        Monitor.of("click", List.of("clickUrl1", "clickUrl2")),
                        Monitor.of("imp", List.of("impUrl3", "impUrl4")),
                        Monitor.of("click", List.of("clickUrl3")),
                        Monitor.of("playStart", List.of("playUrl1", "playUrl2")),
                        Monitor.of("vastError", List.of("vastErrorUrl1", "vastErrorUrl2"))
                ))
                .metaData(metaData)
                .build();
        final Request nativeRequest = Request.builder()
                .ver("3.0")
                .assets(List.of(
                        Asset.builder().id(11).img(ImageObject.builder().type(1).build()).build(),
                        Asset.builder().id(12).img(ImageObject.builder().type(1).build()).build(),
                        Asset.builder().id(13).img(ImageObject.builder().type(2).build()).build(),
                        Asset.builder().id(14).img(ImageObject.builder().type(3).build()).build(),

                        Asset.builder().id(21).data(DataObject.builder().type(2).build()).build(),
                        Asset.builder().id(22).data(DataObject.builder().type(10).build()).build(),
                        Asset.builder().id(23).data(DataObject.builder().type(3).build()).build(),

                        Asset.builder().id(31).title(TitleObject.builder().build()).build(),

                        Asset.builder().id(41).video(VideoObject.builder().build()).build(),

                        Asset.builder().id(51).build()
                ))
                .build();
        final Native xNative = Native.builder().request(mapper.writeValueAsString(nativeRequest)).build();

        // when
        final HuaweiAdm actual = target.buildNative(AdsType.NATIVE, content, xNative);

        // then
        final String expectedVideoAssetAdm = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>"
                + "<VAST version=\\\"3.0\\\">"
                + "<Ad id=\\\"contentId\\\"><InLine>"
                + "<AdSystem>HuaweiAds</AdSystem>"
                + "<AdTitle>title title</AdTitle>"
                + "<Error><![CDATA[vastErrorUrl1&et=[ERRORCODE]]]></Error>"
                + "<Error><![CDATA[vastErrorUrl2&et=[ERRORCODE]]]></Error>"
                + "<Impression><![CDATA[impUrl3]]></Impression><Impression><![CDATA[impUrl4]]></Impression>"
                + "<Creatives>"
                + "<Creative adId=\\\"contentId\\\" id=\\\"contentId\\\">"
                + "<Linear>"
                + "<Duration>00:00:00.100</Duration>"
                + "<TrackingEvents>"
                + "<Tracking event=\\\"start\\\"><![CDATA[playUrl1]]></Tracking>"
                + "<Tracking event=\\\"start\\\"><![CDATA[playUrl2]]></Tracking>"
                + "</TrackingEvents>"
                + "<VideoClicks>"
                + "<ClickThrough><![CDATA[clickUrl]]></ClickThrough>"
                + "<ClickTracking><![CDATA[clickUrl3]]></ClickTracking>"
                + "</VideoClicks>"
                + "<MediaFiles>"
                + "<MediaFile delivery=\\\"progressive\\\" type=\\\"video/mp4\\\" "
                + "width=\\\"300\\\" height=\\\"300\\\" scalable=\\\"true\\\" maintainAspectRatio=\\\"true\\\"> "
                + "<![CDATA[videoUrl]]>"
                + "</MediaFile>"
                + "</MediaFiles>"
                + "</Linear>"
                + "</Creative>"
                + "</Creatives>"
                + "</InLine></Ad></VAST>";
        final String expectedAdm = "{"
                + "\"ver\":\"3.0\","
                + "\"assets\":["
                + "{\"id\":11,\"img\":{\"type\":1,\"url\":\"iconUrl\"}},"
                + "{\"id\":12,\"img\":{\"type\":1,\"url\":\"\",\"w\":100,\"h\":100}},"
                + "{\"id\":13,\"img\":{\"type\":2,\"url\":\"imageInfoUrl\",\"w\":200,\"h\":0}},"
                + "{\"id\":14,\"img\":{\"type\":3,\"w\":200,\"h\":200}},"
                + "{\"id\":21,\"data\":{\"value\":\"title title\",\"label\":\"desc\"}},"
                + "{\"id\":22,\"data\":{\"value\":\"title title\",\"label\":\"desc\"}},"
                + "{\"id\":23,\"data\":{\"value\":\"\",\"label\":\"\"}},"
                + "{\"id\":31,\"title\":{\"text\":\"title title\",\"len\":11}},"
                + "{\"id\":41,\"video\":{\"vasttag\":\"" + expectedVideoAssetAdm + "\"}},"
                + "{\"id\":51}"
                + "],"
                + "\"link\":{\"url\":\"clickUrl\",\"clicktrackers\":[\"clickUrl1\",\"clickUrl2\",\"clickUrl3\"]},"
                + "\"eventtrackers\":["
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl1\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl2\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl3\"},"
                + "{\"event\":1,\"method\":1,\"url\":\"impUrl4\"}]"
                + "}";

        assertThat(actual).isEqualTo(HuaweiAdm.of(expectedAdm, 300, 300));
    }

}
