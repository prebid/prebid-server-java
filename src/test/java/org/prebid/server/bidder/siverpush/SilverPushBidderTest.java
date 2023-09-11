package org.prebid.server.bidder.siverpush;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.silverpush.SilverPushBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.silverpush.ExtImpSilverPush;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class SilverPushBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com";

    private SilverPushBidder target;

    @Before
    public void setUp() {
        target = new SilverPushBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SilverPushBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldFailOnMissingPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSilverPush.of(null, BigDecimal.ONE)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing publisherId parameter."));
    }

    @Test
    public void makeHttpRequestsShouldPassEidsFromDataToExtEids() {
        // given
        final List<Eid> givenEids =
                List.of(Eid.of("testSource", List.of(Uid.of("testUidId", 2, null)), null));
        final ObjectNode givenDataNode = mapper.createObjectNode();
        givenDataNode.set("eids", mapper.valueToTree(givenEids));
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .user(User.builder()
                                .ext(ExtUser.builder().data(givenDataNode).build()).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getUser)
                .map(User::getExt)
                .map(ExtUser::getEids)
                .containsExactly(givenEids);
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToWindowsIfUaContainsWindows() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some Windows userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("Windows");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToiOSIfUaContainsIPhone() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some iPhone userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("iOS");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToiOSIfUaContainsiPod() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some IPod userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("iOS");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToiOSIfUaContainsiiPad() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some iPAD userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("iOS");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToMacOsIfUaContainsMacOs() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some Mac OS X userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("macOS");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToAndroidIfUaContainsAndroid() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some Android userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("Android");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToLinuxIfUaContainsLinux() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some Linux userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("Linux");
    }

    @Test
    public void makeHttpRequestsShouldSetDeviceOsToUnknownIfUaDoesNotMatchAnyKnownPattern() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some special userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getOs)
                .containsExactly("Unknown");
    }

    @Test
    public void makeHttpRequestsShouldSetMobileDeviceTypeIfUserAgentIsIos() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some ios userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetMobileDeviceTypeIfUserAgentIsIpod() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some ipod userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetMobileDeviceTypeIfUserAgentIsIpad() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some ipad userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetMobileDeviceTypeIfUserAgentIsIphone() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some iphone userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetMobileDeviceTypeIfUserAgentIsAndroid() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some android userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsSmartTv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some smarttv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsHbbTv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some hbbtv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsAppleTv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some appletv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsGoogleTv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some googletv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsHdmi() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some hdmi userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsNetCastTv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some netcast.tv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsViera() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some viera userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsNettv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some nettv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsRoku() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some roku userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsDtv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some dtv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsSonydTv() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some sonydtv userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetCtvDeviceTypeIfUserAgentIsInetTvBrowser() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .device(Device.builder().ua("Some inettvbrowser userAgent").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getDevice)
                .map(Device::getDevicetype)
                .containsExactly(3);
    }

    @Test
    public void makeHttpRequestsShouldSetSitePublisherIdFromExtParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .site(Site.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getSite)
                .map(Site::getPublisher)
                .map(Publisher::getId)
                .containsExactly("testPublisherId");
    }

    @Test
    public void makeHttpRequestsShouldSetAppPublisherIdFromExtParams() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                        .app(App.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getApp)
                .map(App::getPublisher)
                .map(Publisher::getId)
                .containsExactly("testPublisherId");
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedExtRequestBidderConfigProperty() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getExt)
                .map(extRequest -> extRequest.getProperty("bc"))
                .containsExactly(new TextNode("sp_pb_ortb_1.0.0"));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedExtRequestPublisherProperty() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .map(BidRequest::getExt)
                .map(extRequest -> extRequest.getProperty("publisherId"))
                .containsExactly(new TextNode("testPublisherId"));
    }

    @Test
    public void makeHttpRequestsShouldFailIfNoValidBannerSizes() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No sizes provided for Banner."));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerSizesFromFirstFormat() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .banner(Banner.builder()
                        .format(singletonList(Format.builder()
                                .w(10)
                                .h(12)
                                .build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .map(Imp::getBanner)
                .map(Banner::getW, Banner::getH)
                .containsExactly(Tuple.tuple(10, 12));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoMissedApiField() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .mimes(List.of("mime1", "mime2"))
                        .protocols(List.of(4, 5, 6))
                        .minduration(0)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid or missing video field(s)"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoMissedMimesField() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .protocols(List.of(4, 5, 6))
                        .minduration(0)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid or missing video field(s)"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoMissedProtocolsField() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .mimes(List.of("mime1", "mime2"))
                        .minduration(0)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid or missing video field(s)"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoMissedMinDurationField() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .mimes(List.of("mime1", "mime2"))
                        .protocols(List.of(4, 5, 6))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid or missing video field(s)"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoMinDurationFieldIsLessThanZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .mimes(List.of("mime1", "mime2"))
                        .protocols(List.of(4, 5, 6))
                        .minduration(-1)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Invalid or missing video field(s)"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetDefaultMaxDuration() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .mimes(List.of("mime1", "mime2"))
                        .protocols(List.of(4, 5, 6))
                        .minduration(0)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .map(Imp::getVideo)
                .map(Video::getMaxduration)
                .containsExactly(120);
    }

    @Test
    public void makeHttpRequestsShouldSetDefaultMixDurationIfMinDurationHigherThanMaxDuration() {
        // given
        final BidRequest bidRequest = givenBidRequest(requestBuilder -> requestBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .mimes(List.of("mime1", "mime2"))
                        .protocols(List.of(4, 5, 6))
                        .minduration(20)
                        .maxduration(15)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .map(Imp::getVideo)
                .map(Video::getMinduration)
                .containsExactly(0);
    }

    @Test
    public void makeHttpRequestsShouldSetExtParamBidFloorIfValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .map(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerBidFloorIfExtParamBidFloorInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(Banner.builder().w(10).h(12).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSilverPush.of("testPublisherId", BigDecimal.ZERO)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .map(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(0.05));
    }

    @Test
    public void makeHttpRequestsShouldSetVideoBidFloorIfExtParamBidFloorInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .video(Video.builder()
                        .api(List.of(1, 2, 3))
                        .mimes(List.of("mime1", "mime2"))
                        .protocols(List.of(4, 5, 6))
                        .minduration(0)
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSilverPush.of("testPublisherId", BigDecimal.ZERO)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .map(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .map(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(0.1));
    }

    @Test
    public void makeHttpRequestsShouldCreateExpectedUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Bad Server Response");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsMissing() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.id("123").mtype(null))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Missing MType for bid: 123"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsNotSupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.id("123").mtype(3))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Unable to resolve mediaType 3 for bid: 123"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMTypeRefersToBanner() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.mtype(1))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(bidBuilder -> bidBuilder.mtype(1)), banner, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMTypeRefersToVideo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid(bidBuilder -> bidBuilder.mtype(2)), video, null));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpSilverPush.of("testPublisherId", BigDecimal.ONE)))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()).build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
