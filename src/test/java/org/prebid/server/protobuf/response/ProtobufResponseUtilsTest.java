package org.prebid.server.protobuf.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.protobuf.Extension;
import com.google.protobuf.Message;
import com.iab.openrtb.response.Asset;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import com.iab.openrtb.response.TitleObject;
import com.iab.openrtb.response.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.openrtb.v2.OpenRtbTest;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class ProtobufResponseUtilsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> titleMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> dataMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> videoMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> imageMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> assetMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> eventTrackerMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse, Response> nativeResponseMapper;

    @Mock
    private ProtobufMapper<OpenRtb.NativeResponse, String> nativeResponseStringMapper;

    @Mock
    private ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> bidMapper;

    @Mock
    private ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> seatBidMapper;

    @BeforeEach
    public void setUp() {
        given(titleMapper.map(givenProtobufNativeTitle())).willReturn(givenNativeTitle());
        given(dataMapper.map(givenProtobufNativeData())).willReturn(givenNativeData());
        given(videoMapper.map(givenProtobufNativeVideo())).willReturn(givenNativeVideo());
        given(imageMapper.map(givenProtobufNativeImage())).willReturn(givenNativeImage());
        given(linkMapper.map(givenProtobufNativeLink())).willReturn(givenNativeLink());
        given(assetMapper.map(givenProtobufNativeAssetWithData())).willReturn(givenAssetWithData());
        given(assetMapper.map(givenProtobufNativeAssetWithVideo())).willReturn(givenAssetWithVideo());
        given(assetMapper.map(givenProtobufNativeAssetWithImage())).willReturn(givenAssetWithImage());
        given(assetMapper.map(givenProtobufNativeAssetWithTitle())).willReturn(givenAssetWithTitle());
        given(eventTrackerMapper.map(givenProtobufEventTracker())).willReturn(givenEventTracker());
        given(nativeResponseMapper.map(givenProtobufNativeResponse())).willReturn(givenNativeResponse());
        given(nativeResponseStringMapper.map(givenProtobufNativeResponse()))
                .willReturn(jacksonMapper.encodeToString(givenNativeResponse()));
        given(bidMapper.map(givenProtobufBidWithPlainAdm())).willReturn(givenBidWithPlainAdm());
        given(seatBidMapper.map(givenProtobufSeatBid())).willReturn(givenSeatBid());
    }

    @Test
    public void bidResponseMapperShouldReturnValidMapperForMapperSpec() {
        // given
        final OpenRtb.BidResponse bidResponse = givenProtobufBidResponse();
        final ResponseExtensionMappersSpecification spec =
                ResponseExtensionMappersSpecification.builder(jacksonMapper.mapper())
                        .bidResponseExtMapper(givenBidResponseExtMapper())
                        .bidExtMapper(givenExtensionJsonMapper(OpenRtbTest.bid))
                        .dataExtMapper(givenExtensionJsonMapper(OpenRtbTest.responseData))
                        .videoExtMapper(givenExtensionJsonMapper(OpenRtbTest.responseVideo))
                        .imageExtMapper(givenExtensionJsonMapper(OpenRtbTest.responseImage))
                        .titleExtMapper(givenExtensionJsonMapper(OpenRtbTest.responseTitle))
                        .linkExtMapper(givenExtensionJsonMapper(OpenRtbTest.responseLink))
                        .assetExtMapper(givenExtensionJsonMapper(OpenRtbTest.responseAsset))
                        .nativeResponseExtMapper(givenExtensionJsonMapper(OpenRtbTest.nativeResponse))
                        .eventTrackerExtMapper(givenExtensionJsonMapper(OpenRtbTest.eventTracker))
                        .seatBidExtMapper(givenExtensionJsonMapper(OpenRtbTest.seatBid))
                        .build();

        // when
        final ProtobufMapper<OpenRtb.BidResponse, BidResponse> mapper =
                ProtobufResponseUtils.bidResponseMapper(spec);

        // then
        final BidResponse result = mapper.map(bidResponse);
        final BidResponse expectedResult = givenBidResponse();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void bidResponseMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.BidResponse bidResponse = givenProtobufBidResponse();

        // when
        final ProtobufMapper<OpenRtb.BidResponse, BidResponse> mapper =
                ProtobufResponseUtils.bidResponseMapper(seatBidMapper, givenBidResponseExtMapper());

        // then
        final BidResponse result = mapper.map(bidResponse);
        final BidResponse expectedResult = givenBidResponse();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsData() {
        // given
        final OpenRtb.NativeResponse.Asset asset = givenProtobufNativeAssetWithData();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> mapper =
                ProtobufResponseUtils.assetMapper(
                        titleMapper,
                        imageMapper,
                        videoMapper,
                        dataMapper,
                        linkMapper,
                        givenExtensionJsonMapper(OpenRtbTest.responseAsset));

        // then
        final Asset result = mapper.map(asset);
        final Asset expectedResult = givenAssetWithData();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsVideo() {
        // given
        final OpenRtb.NativeResponse.Asset asset = givenProtobufNativeAssetWithVideo();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> mapper =
                ProtobufResponseUtils.assetMapper(
                        titleMapper,
                        imageMapper,
                        videoMapper,
                        dataMapper,
                        linkMapper,
                        givenExtensionJsonMapper(OpenRtbTest.responseAsset));

        // then
        final Asset result = mapper.map(asset);
        final Asset expectedResult = givenAssetWithVideo();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsImage() {
        // given
        final OpenRtb.NativeResponse.Asset asset = givenProtobufNativeAssetWithImage();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> mapper =
                ProtobufResponseUtils.assetMapper(
                        titleMapper,
                        imageMapper,
                        videoMapper,
                        dataMapper,
                        linkMapper,
                        givenExtensionJsonMapper(OpenRtbTest.responseAsset));

        // then
        final Asset result = mapper.map(asset);
        final Asset expectedResult = givenAssetWithImage();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsTitle() {
        // given
        final OpenRtb.NativeResponse.Asset asset = givenProtobufNativeAssetWithTitle();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> mapper =
                ProtobufResponseUtils.assetMapper(
                        titleMapper,
                        imageMapper,
                        videoMapper,
                        dataMapper,
                        linkMapper,
                        givenExtensionJsonMapper(OpenRtbTest.responseAsset));

        // then
        final Asset result = mapper.map(asset);
        final Asset expectedResult = givenAssetWithTitle();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void bidMapperShouldReturnMapperWhichMapsAdmNative() {
        // given
        final OpenRtb.BidResponse.SeatBid.Bid bid = givenProtobufBidWithAdmNative();

        // when
        final ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> mapper =
                ProtobufResponseUtils.bidMapper(nativeResponseStringMapper, givenExtensionJsonMapper(OpenRtbTest.bid));

        // then
        final Bid result = mapper.map(bid);
        final Bid expectedResult = givenBidWithAdmNative();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void bidMapperShouldReturnMapperWhichMapsPlainAdm() {
        // given
        final OpenRtb.BidResponse.SeatBid.Bid bid = givenProtobufBidWithPlainAdm();

        // when
        final ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> mapper =
                ProtobufResponseUtils.bidMapper(nativeResponseStringMapper, givenExtensionJsonMapper(OpenRtbTest.bid));

        // then
        final Bid result = mapper.map(bid);
        final Bid expectedResult = givenBidWithPlainAdm();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeDataMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse.Asset.Data data = givenProtobufNativeData();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> mapper =
                ProtobufResponseUtils.nativeDataMapper(givenExtensionJsonMapper(OpenRtbTest.responseData));

        // then
        final DataObject result = mapper.map(data);
        final DataObject expectedResult = givenNativeData();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void eventTrackerMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse.EventTracker eventTracker = givenProtobufEventTracker();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> mapper =
                ProtobufResponseUtils.eventTrackerMapper(givenExtensionJsonMapper(OpenRtbTest.eventTracker));

        // then
        final EventTracker result = mapper.map(eventTracker);
        final EventTracker expectedResult = givenEventTracker();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeImageMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse.Asset.Image image = givenProtobufNativeImage();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> mapper =
                ProtobufResponseUtils.nativeImageMapper(givenExtensionJsonMapper(OpenRtbTest.responseImage));

        // then
        final ImageObject result = mapper.map(image);
        final ImageObject expectedResult = givenNativeImage();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void linkMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse.Link link = givenProtobufNativeLink();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Link, Link> mapper =
                ProtobufResponseUtils.linkMapper(givenExtensionJsonMapper(OpenRtbTest.responseLink));

        // then
        final Link result = mapper.map(link);
        final Link expectedResult = givenNativeLink();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeResponseMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse nativeResponse = givenProtobufNativeResponse();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse, Response> mapper =
                ProtobufResponseUtils.nativeResponseMapper(
                        assetMapper,
                        linkMapper,
                        eventTrackerMapper,
                        givenExtensionJsonMapper(OpenRtbTest.nativeResponse));

        // then
        final Response result = mapper.map(nativeResponse);
        final Response expectedResult = givenNativeResponse();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeResponseStringMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse nativeResponse = givenProtobufNativeResponse();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse, String> mapper =
                ProtobufResponseUtils.nativeResponseMapper(jacksonMapper.mapper(), nativeResponseMapper);

        // then
        final String result = mapper.map(nativeResponse);
        final String expectedResult = jacksonMapper.encodeToString(givenNativeResponse());

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void seatBidMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.BidResponse.SeatBid seatBid = givenProtobufSeatBid();

        // when
        final ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> mapper =
                ProtobufResponseUtils.seatBidMapper(bidMapper, givenExtensionJsonMapper(OpenRtbTest.seatBid));

        // then
        final SeatBid result = mapper.map(seatBid);
        final SeatBid expectedResult = givenSeatBid();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void titleMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse.Asset.Title title = givenProtobufNativeTitle();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> mapper =
                ProtobufResponseUtils.titleMapper(givenExtensionJsonMapper(OpenRtbTest.responseTitle));

        // then
        final TitleObject result = mapper.map(title);
        final TitleObject expectedResult = givenNativeTitle();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeVideoMapperShouldReturnValidMapper() {
        // given
        final OpenRtb.NativeResponse.Asset.Video video = givenProtobufNativeVideo();

        // when
        final ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> mapper =
                ProtobufResponseUtils.nativeVideoMapper();

        // then
        final VideoObject result = mapper.map(video);
        final VideoObject expectedResult = givenNativeVideo();

        assertThat(result).isEqualTo(expectedResult);
    }

    private static BidResponse givenBidResponse() {
        final ExtBidResponsePrebid extBidResponsePrebid = ExtBidResponsePrebid.builder()
                .passthrough(TextNode.valueOf("fieldValue"))
                .build();
        final ExtBidResponse extBidResponse = ExtBidResponse.builder().prebid(extBidResponsePrebid).build();

        return BidResponse.builder()
                .seatbid(singletonList(givenSeatBid()))
                .id("id")
                .cur("cur")
                .nbr(1)
                .bidid("bidid")
                .customdata("customdata")
                .ext(extBidResponse)
                .build();
    }

    private static OpenRtb.BidResponse givenProtobufBidResponse() {
        return OpenRtb.BidResponse.newBuilder()
                .addSeatbid(givenProtobufSeatBid())
                .setId("id")
                .setCur("cur")
                .setNbr(1)
                .setBidid("bidid")
                .setCustomdata("customdata")
                .setExtension(OpenRtbTest.bidResponse, givenProtobufExt("fieldValue"))
                .build();
    }

    private static SeatBid givenSeatBid() {
        return SeatBid.builder()
                .bid(singletonList(givenBidWithPlainAdm()))
                .seat("seat")
                .group(1)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidResponse.SeatBid givenProtobufSeatBid() {
        return OpenRtb.BidResponse.SeatBid.newBuilder()
                .addBid(givenProtobufBidWithPlainAdm())
                .setSeat("seat")
                .setGroup(true)
                .setExtension(OpenRtbTest.seatBid, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Bid givenBidWithPlainAdm() {
        return givenBid().toBuilder()
                .adm("adm")
                .build();
    }

    private static Bid givenBidWithAdmNative() {
        return givenBid().toBuilder()
                .adm(jacksonMapper.encodeToString(givenNativeResponse()))
                .build();
    }

    private static Bid givenBid() {
        return Bid.builder()
                .id("id")
                .impid("impid")
                .price(BigDecimal.valueOf(1.0))
                .nurl("nurl")
                .burl("burl")
                .lurl("lurl")
                .adid("adid")
                .adomain(singletonList("adomain"))
                .bundle("bundle")
                .iurl("iurl")
                .cid("cid")
                .crid("crid")
                .tactic("tactic")
                .cat(singletonList("cat"))
                .attr(singletonList(1))
                .api(2)
                .protocol(3)
                .qagmediarating(4)
                .language("language")
                .dealid("dealid")
                .w(5)
                .h(6)
                .wratio(7)
                .hratio(8)
                .exp(9)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidResponse.SeatBid.Bid givenProtobufBidWithPlainAdm() {
        return OpenRtb.BidResponse.SeatBid.Bid.newBuilder(givenProtobufBid())
                .setAdm("adm")
                .build();
    }

    private static OpenRtb.BidResponse.SeatBid.Bid givenProtobufBidWithAdmNative() {
        return OpenRtb.BidResponse.SeatBid.Bid.newBuilder(givenProtobufBid())
                .setAdmNative(givenProtobufNativeResponse())
                .build();
    }

    private static OpenRtb.BidResponse.SeatBid.Bid givenProtobufBid() {
        return OpenRtb.BidResponse.SeatBid.Bid.newBuilder()
                .setId("id")
                .setImpid("impid")
                .setPrice(1.0)
                .setNurl("nurl")
                .setBurl("burl")
                .setLurl("lurl")
                .setAdid("adid")
                .addAdomain("adomain")
                .setBundle("bundle")
                .setIurl("iurl")
                .setCid("cid")
                .setCrid("crid")
                .setTactic("tactic")
                .addCat("cat")
                .addAttr(1)
                .setApi(2)
                .setProtocol(3)
                .setQagmediarating(4)
                .setLanguage("language")
                .setDealid("dealid")
                .setW(5)
                .setH(6)
                .setWratio(7)
                .setHratio(8)
                .setExp(9)
                .setExtension(OpenRtbTest.bid, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Response givenNativeResponse() {
        return Response.builder()
                .eventtrackers(singletonList(givenEventTracker()))
                .assets(singletonList(givenAssetWithData()))
                .link(givenNativeLink())
                .imptrackers(singletonList("imptrackers"))
                .ver("ver")
                .privacy("privacy")
                .assetsurl("asseturl")
                .jstracker("jstracker")
                .dcourl("dcourl")
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeResponse givenProtobufNativeResponse() {
        return OpenRtb.NativeResponse.newBuilder()
                .addEventtrackers(givenProtobufEventTracker())
                .addAssets(givenProtobufNativeAssetWithData())
                .setLink(givenProtobufNativeLink())
                .addImptrackers("imptrackers")
                .setVer("ver")
                .setPrivacy("privacy")
                .setAssetsurl("asseturl")
                .setJstracker("jstracker")
                .setDcourl("dcourl")
                .setExtension(OpenRtbTest.nativeResponse, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Asset givenAssetWithVideo() {
        return givenAsset(assetBuilder -> assetBuilder.video(givenNativeVideo()));
    }

    private static Asset givenAssetWithImage() {
        return givenAsset(assetBuilder -> assetBuilder.img(givenNativeImage()));
    }

    private static Asset givenAssetWithData() {
        return givenAsset(assetBuilder -> assetBuilder.data(givenNativeData()));
    }

    private static Asset givenAssetWithTitle() {
        return givenAsset(assetBuilder -> assetBuilder.title(givenNativeTitle()));
    }

    private static Asset givenAsset(UnaryOperator<Asset.AssetBuilder> assetModifier) {
        final Asset.AssetBuilder assetBuilder = Asset.builder()
                .id(1)
                .required(1)
                .ext(givenJsonExt("fieldValue"));

        return assetModifier.apply(assetBuilder).build();
    }

    private static OpenRtb.NativeResponse.Asset givenProtobufNativeAssetWithVideo() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setVideo(givenProtobufNativeVideo()));
    }

    private static OpenRtb.NativeResponse.Asset givenProtobufNativeAssetWithImage() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setImg(givenProtobufNativeImage()));
    }

    private static OpenRtb.NativeResponse.Asset givenProtobufNativeAssetWithData() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setData(givenProtobufNativeData()));
    }

    private static OpenRtb.NativeResponse.Asset givenProtobufNativeAssetWithTitle() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setTitle(givenProtobufNativeTitle()));
    }

    private static OpenRtb.NativeResponse.Asset givenProtobufNativeAsset(
            UnaryOperator<OpenRtb.NativeResponse.Asset.Builder> assetModifier) {

        final OpenRtb.NativeResponse.Asset.Builder assetBuilder = OpenRtb.NativeResponse.Asset.newBuilder()
                .setId(1)
                .setRequired(true)
                .setExtension(OpenRtbTest.responseAsset, givenProtobufExt("fieldValue"));

        return assetModifier.apply(assetBuilder).build();
    }

    private static DataObject givenNativeData() {
        return DataObject.builder()
                .type(1)
                .value("value")
                .len(2)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeResponse.Asset.Data givenProtobufNativeData() {
        return OpenRtb.NativeResponse.Asset.Data.newBuilder()
                .setType(1)
                .setValue("value")
                .setLen(2)
                .setExtension(OpenRtbTest.responseData, givenProtobufExt("fieldValue"))
                .build();
    }

    private static EventTracker givenEventTracker() {
        return EventTracker.builder()
                .event(1)
                .url("url")
                .method(2)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeResponse.EventTracker givenProtobufEventTracker() {
        return OpenRtb.NativeResponse.EventTracker.newBuilder()
                .setEvent(1)
                .setUrl("url")
                .setMethod(2)
                .setExtension(OpenRtbTest.eventTracker, givenProtobufExt("fieldValue"))
                .build();
    }

    private static ImageObject givenNativeImage() {
        return ImageObject.builder()
                .type(1)
                .w(2)
                .h(3)
                .url("url")
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeResponse.Asset.Image givenProtobufNativeImage() {
        return OpenRtb.NativeResponse.Asset.Image.newBuilder()
                .setType(1)
                .setW(2)
                .setH(3)
                .setUrl("url")
                .setExtension(OpenRtbTest.responseImage, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Link givenNativeLink() {
        return Link.of("url", singletonList("clicktrackers"), "fallback", givenJsonExt("fieldValue"));
    }

    private static OpenRtb.NativeResponse.Link givenProtobufNativeLink() {
        return OpenRtb.NativeResponse.Link.newBuilder()
                .setUrl("url")
                .addClicktrackers("clicktrackers")
                .setFallback("fallback")
                .setExtension(OpenRtbTest.responseLink, givenProtobufExt("fieldValue"))
                .build();
    }

    private static TitleObject givenNativeTitle() {
        return TitleObject.builder()
                .text("text")
                .len(1)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeResponse.Asset.Title givenProtobufNativeTitle() {
        return OpenRtb.NativeResponse.Asset.Title.newBuilder()
                .setText("text")
                .setLen(1)
                .setExtension(OpenRtbTest.responseTitle, givenProtobufExt("fieldValue"))
                .build();
    }

    private static VideoObject givenNativeVideo() {
        return VideoObject.builder()
                .vasttag("vasttag")
                .build();
    }

    private static OpenRtb.NativeResponse.Asset.Video givenProtobufNativeVideo() {
        return OpenRtb.NativeResponse.Asset.Video.newBuilder()
                .setVasttag("vasttag")
                .build();
    }

    private static ObjectNode givenJsonExt(String fieldValue) {
        return jacksonMapper.mapper().createObjectNode()
                .put("field", fieldValue);
    }

    private static OpenRtbTest.TestExt givenProtobufExt(String fieldValue) {
        return OpenRtbTest.TestExt.newBuilder()
                .setField(fieldValue)
                .build();
    }

    private static ProtobufBackwardExtensionMapper<
            OpenRtb.BidResponse,
            OpenRtbTest.TestExt,
            ExtBidResponse> givenBidResponseExtMapper() {

        return new ProtobufBackwardExtensionMapper<>() {
            @Override
            public ExtBidResponse map(OpenRtbTest.TestExt ext) {
                final TextNode passThrough = TextNode.valueOf(ext.getField());
                return ExtBidResponse.builder()
                        .prebid(ExtBidResponsePrebid.builder().passthrough(passThrough).build())
                        .build();
            }

            @Override
            public Extension<OpenRtb.BidResponse, OpenRtbTest.TestExt> extensionDescriptor() {
                return OpenRtbTest.bidResponse;
            }
        };
    }

    private static <ContainingType extends Message>
            ProtobufJsonExtensionMapper<ContainingType, OpenRtbTest.TestExt> givenExtensionJsonMapper(
            Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor) {

        return new ProtobufJsonExtensionMapper<>() {
            @Override
            public ObjectNode map(OpenRtbTest.TestExt ext) {
                return givenJsonExt(ext.getField());
            }

            @Override
            public Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor() {
                return extensionDescriptor;
            }
        };
    }
}
