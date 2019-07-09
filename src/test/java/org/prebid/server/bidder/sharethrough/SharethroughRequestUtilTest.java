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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SharethroughRequestUtilTest {

    @Test
    public void getPageShouldReturnNullWhenSiteIsNull() {
        // given when and then
        assertNull(SharethroughRequestUtil.getPage(null));
    }

    @Test
    public void getPageShouldReturnPageWhenSitePresent() {
        // given
        final String page = "page";
        final Site site = Site.builder().page(page).build();

        // when and then
        assertEquals(page, SharethroughRequestUtil.getPage(site));
    }

    @Test
    public void getUaShouldReturnNullWhenDeviceIsNull() {
        // given when and then
        assertNull(SharethroughRequestUtil.getUa(null));
    }

    @Test
    public void getUaShouldReturnUaWhenDevicePresent() {
        // given
        final String ua = "ua";
        final Device device = Device.builder().ua(ua).build();

        // when and then
        assertEquals(ua, SharethroughRequestUtil.getUa(device));
    }

    @Test
    public void getHostShouldReturnEmptyStringWhenStringIsNotUri() {
        // given when and then
        assertEquals("", SharethroughRequestUtil.getHost(null));
        assertEquals("", SharethroughRequestUtil.getHost(""));
        assertEquals("", SharethroughRequestUtil.getHost("not uri"));
        assertEquals("", SharethroughRequestUtil.getHost("asdsadqzx"));
    }

    @Test
    public void getHostShouldReturnHostWhenStringUri() {
        // given
        final String firstUri = "https://rubiconproject.com/";
        final String secondUri = "http://a.domain.com/page?param=value";
        final String thirdUri = "http://a.domain.com:8000/page?param=value/";

        // when and then
        assertEquals("rubiconproject.com", SharethroughRequestUtil.getHost(firstUri));
        assertEquals("a.domain.com", SharethroughRequestUtil.getHost(secondUri));
        assertEquals("a.domain.com", SharethroughRequestUtil.getHost(thirdUri));
    }


    @Test
    public void getConsentShouldReturnEmptyStringWhenExtUserOrConsentIsNull() {
        // given
        final ExtUser extUser = ExtUser.builder().consent(null).build();

        // when and then
        assertEquals("", SharethroughRequestUtil.getConsent(null));
        assertEquals("", SharethroughRequestUtil.getConsent(extUser));
    }

    @Test
    public void getConsentShouldReturnConsentWhenExtUserContainsConsent() {
        // given
        final String consent = "consent";
        final ExtUser extUser = ExtUser.builder().consent(consent).build();

        // when and then
        assertEquals(consent, SharethroughRequestUtil.getConsent(extUser));
    }

    @Test
    public void getExtUserShouldReturnNullWhenUserOrUserExtIsNull() {
        // given
        final User user = User.builder().ext(null).build();

        // when and then
        assertNull(SharethroughRequestUtil.getExtUser(null));
        assertNull(SharethroughRequestUtil.getExtUser(user));
    }

    @Test
    public void getExtUserShouldReturnExtUserWhenUserContainsUserExt() {
        // given
        final ExtUser extUser = ExtUser.builder().consent("con")
                .data(Json.mapper.createObjectNode().put("key", "value"))
                .build();
        final User user = User.builder().ext(Json.mapper.valueToTree(extUser)).build();

        // when and then
        assertEquals(extUser, SharethroughRequestUtil.getExtUser(user));
    }

    @Test
    public void canBrowserAutoPlayVideoShouldReturnFalseWhenUserAgentIsBlank() {
        // given when and then
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(null));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(""));
    }

    @Test
    public void canBrowserAutoPlayVideoShouldReturnTrueWhenUserAgentIsNotParsed() {
        // given
        final String firstUserAgent = "strange behavior";
        final String secondUserAgent = "very very strange behavior";

        // when and then
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(firstUserAgent));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(secondUserAgent));
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
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent1));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent2));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent3));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent1));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent2));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent3));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent1));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent2));
        assertTrue(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent3));
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
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent1));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent2));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(androidUserAgent3));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent1));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent2));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosChromeUserAgent3));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent1));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent2));
        assertFalse(SharethroughRequestUtil.canBrowserAutoPlayVideo(iosSafariUserAgent3));
    }

    @Test
    public void isConsentRequiredShouldReturnFalseWhenRegsOrRegsExtIsNull() {
        // given
        final Regs regs = Regs.of(null, null);
        final Regs regsWithGdprNull = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(null)));

        // when and then
        assertFalse(SharethroughRequestUtil.isConsentRequired(null));
        assertFalse(SharethroughRequestUtil.isConsentRequired(regs));
        assertFalse(SharethroughRequestUtil.isConsentRequired(regsWithGdprNull));
    }

    @Test
    public void isConsentRequiredShouldReturnFalseWhenRegsExtIsNot1() {
        // given
        final Regs regsWith3 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(3)));
        final Regs regsWith0 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(0)));
        final Regs regsWith100 = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(100)));

        // when and then
        assertFalse(SharethroughRequestUtil.isConsentRequired(regsWith0));
        assertFalse(SharethroughRequestUtil.isConsentRequired(regsWith3));
        assertFalse(SharethroughRequestUtil.isConsentRequired(regsWith100));
    }

    @Test
    public void isConsentRequiredShouldReturnTrueWhenRegsExtIs1() {
        // given
        final Regs regs = Regs.of(null, Json.mapper.valueToTree(ExtRegs.of(1)));

        // when and then
        assertTrue(SharethroughRequestUtil.isConsentRequired(regs));
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

        assertEquals(expected, SharethroughRequestUtil.getSize(notBannerImp, extImpSharethrough));
        assertEquals(expected, SharethroughRequestUtil.getSize(bannerImpEmptyFormat, extImpSharethrough));
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

        assertEquals(expected, SharethroughRequestUtil.getSize(imp, extImpSharethrough));
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

        assertEquals(expected, SharethroughRequestUtil.getSize(imp, extImpSharethrough));
    }
}

