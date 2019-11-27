package org.prebid.server.bidder.sharethrough;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.json.Json;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.sharethrough.model.Size;
import org.prebid.server.bidder.sharethrough.model.UserInfo;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SharethroughRequestUtilTest extends VertxTest {

    @Test
    public void getPageShouldReturnNullWhenSiteIsNull() {
        // given when and then
        assertThat(SharethroughRequestUtil.getPage(null)).isNull();
    }

    @Test
    public void getPageShouldReturnPageWhenSitePresent() {
        // given
        final String page = "page";
        final Site site = Site.builder().page(page).build();

        // when and then
        assertThat(SharethroughRequestUtil.getPage(site)).isEqualTo(page);
    }

    @Test
    public void getHostShouldReturnEmptyStringWhenStringIsNotUri() {
        // given when and then
        assertThat(SharethroughRequestUtil.getHost(null)).isEmpty();
        assertThat(SharethroughRequestUtil.getHost("")).isEmpty();
        assertThat(SharethroughRequestUtil.getHost("not uri")).isEmpty();
        assertThat(SharethroughRequestUtil.getHost("asdsadqzx")).isEmpty();
    }

    @Test
    public void getHostShouldReturnHostWhenStringUri() {
        // given
        final String firstUri = "https://rubiconproject.com/";
        final String secondUri = "http://a.domain.com/page?param=value";
        final String thirdUri = "http://a.domain.com:8000/page?param=value/";

        // when and then
        assertThat(SharethroughRequestUtil.getHost(firstUri)).isEqualTo("https://rubiconproject.com");
        assertThat(SharethroughRequestUtil.getHost(secondUri)).isEqualTo("http://a.domain.com");
        assertThat(SharethroughRequestUtil.getHost(thirdUri)).isEqualTo("http://a.domain.com");
    }


    @Test
    public void retrieveFromUserInfoShouldReturnEmptyStringWhenUserInfoOrParameterIsNull() {
        // given
        final UserInfo userInfo = UserInfo.of(null, null, null);

        // when and then
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(null, UserInfo::getConsent)).isEmpty();
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(userInfo, UserInfo::getConsent)).isEmpty();
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(userInfo, UserInfo::getTtdUid)).isEmpty();
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(userInfo, UserInfo::getStxuid)).isEmpty();
    }

    @Test
    public void retrieveFromUserInfoShouldReturnConsentWhenExtUserContainsParameter() {
        // given
        final String consent = "consent";
        final String ttduid = "ttduid";
        final String stxuid = "stxuid";
        final UserInfo userInfo = UserInfo.of(consent, ttduid, stxuid);

        // when and then
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(userInfo, UserInfo::getConsent)).isEqualTo(consent);
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(userInfo, UserInfo::getTtdUid)).isEqualTo(ttduid);
        assertThat(SharethroughRequestUtil.retrieveFromUserInfo(userInfo, UserInfo::getStxuid)).isEqualTo(stxuid);
    }

    @Test
    public void getUserInfoShouldReturnUserInfoWithNullWhenUserOrUserExtIsNull() {
        // given
        final User user = User.builder().build();

        // when and then
        final UserInfo expected = UserInfo.of(null, null, null);
        assertThat(SharethroughRequestUtil.getUserInfo(null)).isEqualTo(expected);
        assertThat(SharethroughRequestUtil.getUserInfo(user)).isEqualTo(expected);
    }

    @Test
    public void getUserInfoShouldReturnUserInfoWithConsentWhenUserExtContainsConsent() {
        // given
        final String consent = "con";
        final ExtUser extUser = ExtUser.builder().consent(consent).build();
        final User user = User.builder().ext(Json.mapper.valueToTree(extUser)).build();

        // when and then
        final UserInfo expected = UserInfo.of(consent, null, null);
        assertThat(SharethroughRequestUtil.getUserInfo(user)).isEqualTo(expected);
    }

    @Test
    public void getUserInfoShouldReturnUserInfoWithTtdUidFromFirstExtUserEidUid() {
        // given
        final String consent = "con";
        final List<ExtUserEidUid> uids = Arrays.asList(
                ExtUserEidUid.of("first", null),
                ExtUserEidUid.of("second", null));
        final ExtUserEid extUserEid = ExtUserEid.of("adserver.org", null, uids, null);

        final ExtUser extUser = ExtUser.builder()
                .consent(consent)
                .eids(Collections.singletonList(extUserEid))
                .build();
        final User user = User.builder().buyeruid("buyerid").ext(Json.mapper.valueToTree(extUser)).build();

        // when and then
        final UserInfo expected = UserInfo.of(consent, "first", "buyerid");
        assertThat(SharethroughRequestUtil.getUserInfo(user)).isEqualTo(expected);
    }

    @Test
    public void getUserInfoShouldReturnUserInfoWithTtdUidFromFirstExtUserEidUidFromSecondExtUserEid() {
        // given
        final List<ExtUserEidUid> uidsFromFirst = Arrays.asList(
                ExtUserEidUid.of("firstFromFirst", null),
                ExtUserEidUid.of("secondFromFirst", null));
        final ExtUserEid firstExtUserEid = ExtUserEid.of("badSource", null, uidsFromFirst, null);

        final List<ExtUserEidUid> uidsFromSecond = Arrays.asList(
                ExtUserEidUid.of("firstFromSecond", null),
                ExtUserEidUid.of("secondFromSecond", null));
        final ExtUserEid secondExtUserEid = ExtUserEid.of("adserver.org", null, uidsFromSecond, null);

        final ExtUser extUser = ExtUser.builder()
                .eids(Arrays.asList(firstExtUserEid, secondExtUserEid))
                .build();
        final User user = User.builder().ext(Json.mapper.valueToTree(extUser)).build();

        // when and then
        final UserInfo expected = UserInfo.of(null, "firstFromSecond", null);
        assertThat(SharethroughRequestUtil.getUserInfo(user)).isEqualTo(expected);
    }

    @Test
    public void canBrowserAutoPlayVideoShouldReturnFalseWhenUserAgentIsBlank() {
        // given when and then
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(null)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo("")).isFalse();
    }

    @Test
    public void canBrowserAutoPlayVideoShouldReturnTrueWhenUserAgentIsNotParsed() {
        // given
        final String firstUserAgent = "strange behavior";
        final String secondUserAgent = "very very strange behavior";

        // when and then
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(firstUserAgent)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(secondUserAgent)).isTrue();
    }

    @Test
    public void canBrowserAutoPlayVideoShouldReturnTrueWhenUserAgentIsValid() {
        // given
        final String androidUserAgent1 = "Android Chrome/60.0";
        final String androidUserAgent2 = "Android Chrome/53.0";
        final String androidUserAgent3 = "Android Chrome/54.0";
        final String iosChromeUserAgent1 = "iPhone CriOS/53.0";
        final String iosChromeUserAgent2 = "iPhone CriOS/60.0";
        final String iosChromeUserAgent3 = "iPhone CriOS/54.0";
        final String iosSafariUserAgent1 = "iPad Version/10.0";
        final String iosSafariUserAgent2 = "iPad Version/11.0";
        final String iosSafariUserAgent3 = "iPad Version/14.0";

        // when and then
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent1)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent2)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent3)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent1)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent2)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent3)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent1)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent2)).isTrue();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent3)).isTrue();
    }

    @Test
    public void canBrowserAutoPlayVideoShouldReturnFalseWhenUserAgentIsInvalid() {
        // given
        final String androidUserAgent1 = "Android Chrome/52.0";
        final String androidUserAgent2 = "Android Chrome/10.0";
        final String androidUserAgent3 = "Android Chrome/12.0";
        final String iosChromeUserAgent1 = "iPhone CriOS/52.0";
        final String iosChromeUserAgent2 = "iPhone CriOS/10.0";
        final String iosChromeUserAgent3 = "iPhone CriOS/50.0";
        final String iosSafariUserAgent1 = "iPad Version/5.0";
        final String iosSafariUserAgent2 = "iPad Version/9.0";
        final String iosSafariUserAgent3 = "iPad Version/1.0";

        // when and then
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent1)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent2)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent3)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent1)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent2)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent3)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent1)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent2)).isFalse();
        assertThat(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent3)).isFalse();
    }

    @Test
    public void isConsentRequiredShouldReturnFalseWhenRegsOrRegsExtIsNull() {
        // given
        final Regs regs = Regs.of(null, null);
        final Regs regsWithGdprNull = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(null, null)));

        // when and then
        assertThat(SharethroughRequestUtil.isConsentRequired(null)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regs)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWithGdprNull)).isFalse();
    }

    @Test
    public void isConsentRequiredShouldReturnFalseWhenRegsExtIsNot1() {
        // given
        final Regs regsWith3 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(3, null)));
        final Regs regsWith0 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(0, null)));
        final Regs regsWith100 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(100, null)));

        // when and then
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWith0)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWith3)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWith100)).isFalse();
    }

    @Test
    public void isConsentRequiredShouldReturnTrueWhenRegsExtIs1() {
        // given
        final Regs regs = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(1, null)));

        // when and then
        assertThat(SharethroughRequestUtil.isConsentRequired(regs)).isTrue();
    }

    @Test
    public void getSizeShouldReturnDefaultSize1x1WhenExtImpSizeIsEmptyAndImpIsNotBannerOrImpBannerFormatIsEmpty() {
        // given
        final Imp notBannerImp = Imp.builder().build();
        final Banner banner = Banner.builder().format(Collections.emptyList()).build();
        final Imp bannerImpEmptyFormat = Imp.builder().banner(banner).build();
        final ExtImpSharethrough extImpSharethrough = ExtImpSharethrough.of(null, null, Collections.emptyList(), null);

        // when and then
        final Size expected = Size.of(1, 1);

        assertThat(SharethroughRequestUtil.getSize(notBannerImp, extImpSharethrough)).isEqualTo(expected);
        assertThat(SharethroughRequestUtil.getSize(bannerImpEmptyFormat, extImpSharethrough)).isEqualTo(expected);
    }

    @Test
    public void getSizeShouldReturnExtImpSizeWhenExtImpSizeIsNotEmptyAndNotContainsZero() {
        // given
        final List<Format> formats = Collections.singletonList(Format.builder().w(200).h(200).build());
        final Banner banner = Banner.builder().format(formats).build();
        final Imp imp = Imp.builder().banner(banner).build();
        final ExtImpSharethrough extImpSharethrough = ExtImpSharethrough.of(null, null, Arrays.asList(100, 100), null);

        // when and then
        final Size expected = Size.of(100, 100);

        assertThat(SharethroughRequestUtil.getSize(imp, extImpSharethrough)).isEqualTo(expected);
    }

    @Test
    public void getSizeShouldReturnBiggestSizeFromFormatsWhenExtImpSizeIsEmptyAndImpBannerContainsFormats() {
        // given
        final Format firstFormat = Format.builder().w(200).h(400).build();
        final Format secondFormat = Format.builder().w(500).h(100).build();
        final Format thirdFormat = Format.builder().w(300).h(320).build();
        final List<Format> formats = Arrays.asList(firstFormat, secondFormat, thirdFormat);
        final Banner banner = Banner.builder().format(formats).build();
        final Imp imp = Imp.builder().banner(banner).build();
        final ExtImpSharethrough extImpSharethrough = ExtImpSharethrough.of(null, null, Collections.emptyList(), null);

        // when and then
        final Size expected = Size.of(320, 300);

        assertThat(SharethroughRequestUtil.getSize(imp, extImpSharethrough)).isEqualTo(expected);
    }
}

