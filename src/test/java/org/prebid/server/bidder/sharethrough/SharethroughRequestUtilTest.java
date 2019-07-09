package org.prebid.server.bidder.sharethrough;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.json.Json;
import org.junit.Test;
import org.prebid.server.bidder.sharethrough.model.Size;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SharethroughRequestUtilTest {

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
    public void getUaShouldReturnNullWhenDeviceIsNull() {
        // given when and then
        assertThat(SharethroughRequestUtil.getUa(null)).isNull();
    }

    @Test
    public void getUaShouldReturnUaWhenDevicePresent() {
        // given
        final String ua = "ua";
        final Device device = Device.builder().ua(ua).build();

        // when and then
        assertThat(SharethroughRequestUtil.getUa(device)).isEqualTo(ua);
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
        assertThat(SharethroughRequestUtil.getHost(firstUri)).isEqualTo("rubiconproject.com");
        assertThat(SharethroughRequestUtil.getHost(secondUri)).isEqualTo("a.domain.com");
        assertThat(SharethroughRequestUtil.getHost(thirdUri)).isEqualTo("a.domain.com");
    }


    @Test
    public void getConsentShouldReturnEmptyStringWhenExtUserOrConsentIsNull() {
        // given
        final ExtUser extUser = ExtUser.builder().consent(null).build();

        // when and then
        assertThat(SharethroughRequestUtil.getConsent(null)).isEmpty();
        assertThat(SharethroughRequestUtil.getConsent(extUser)).isEmpty();
    }

    @Test
    public void getConsentShouldReturnConsentWhenExtUserContainsConsent() {
        // given
        final String consent = "consent";
        final ExtUser extUser = ExtUser.builder().consent(consent).build();

        // when and then
        assertThat(SharethroughRequestUtil.getConsent(extUser)).isEqualTo(consent);
    }

    @Test
    public void getExtUserShouldReturnNullWhenUserOrUserExtIsNull() {
        // given
        final User user = User.builder().ext(null).build();

        // when and then
        assertThat(SharethroughRequestUtil.getExtUser(null)).isNull();
        assertThat(SharethroughRequestUtil.getExtUser(user)).isNull();
    }

    @Test
    public void getExtUserShouldReturnExtUserWhenUserContainsUserExt() {
        // given
        final ExtUser extUser = ExtUser.builder().consent("con")
                .data(Json.mapper.createObjectNode().put("key", "value"))
                .build();
        final User user = User.builder().ext(Json.mapper.valueToTree(extUser)).build();

        // when and then
        assertThat(SharethroughRequestUtil.getExtUser(user)).isEqualTo(extUser);
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
        final Regs regsWithGdprNull = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(null)));

        // when and then
        assertThat(SharethroughRequestUtil.isConsentRequired(null)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regs)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWithGdprNull)).isFalse();
    }

    @Test
    public void isConsentRequiredShouldReturnFalseWhenRegsExtIsNot1() {
        // given
        final Regs regsWith3 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(3)));
        final Regs regsWith0 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(0)));
        final Regs regsWith100 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(100)));

        // when and then
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWith0)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWith3)).isFalse();
        assertThat(SharethroughRequestUtil.isConsentRequired(regsWith100)).isFalse();
    }

    @Test
    public void isConsentRequiredShouldReturnTrueWhenRegsExtIs1() {
        // given
        final Regs regs = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(1)));

        // when and then
        assertThat(SharethroughRequestUtil.isConsentRequired(regs)).isTrue();
    }

    @Test
    public void getSizeShouldReturnDefaultSize1x1WhenExtImpSizeIsEmptyAndImpIsNotBannerOrImpBannerFormatIsEmpty() {
        // given
        final Imp notBannerImp = Imp.builder().build();
        final Banner banner = Banner.builder().format(Collections.emptyList()).build();
        final Imp bannerImpEmptyFormat = Imp.builder().banner(banner).build();
        final ExtImpSharethrough extImpSharethrough = ExtImpSharethrough.of(null, null, Collections.emptyList());

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
        final ExtImpSharethrough extImpSharethrough = ExtImpSharethrough.of(null, null, Arrays.asList(100, 100));

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
        final ExtImpSharethrough extImpSharethrough = ExtImpSharethrough.of(null, null, Collections.emptyList());

        // when and then
        final Size expected = Size.of(320, 300);

        assertThat(SharethroughRequestUtil.getSize(imp, extImpSharethrough)).isEqualTo(expected);
    }
}

