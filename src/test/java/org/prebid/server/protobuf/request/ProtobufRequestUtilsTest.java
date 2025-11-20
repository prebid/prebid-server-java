package org.prebid.server.protobuf.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.protobuf.Extension;
import com.google.protobuf.Message;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Asset;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Producer;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.TitleObject;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.openrtb.v2.OpenRtbTest;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class ProtobufRequestUtilsTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> segmentMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> formatMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> dealMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> producerMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Site, OpenRtb.BidRequest.Site> siteMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> nativeTitleMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> nativeImageMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> nativeVideoMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> nativeDataMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> metricMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> videoMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> audioMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> pmpMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> assetMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> eventTrackerMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Request, OpenRtb.NativeRequest> nativeRequestMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<String, OpenRtb.NativeRequest> stringNativeRequestMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> nativeMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> impMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<App, OpenRtb.BidRequest.App> appMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Device, OpenRtb.BidRequest.Device> deviceMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<User, OpenRtb.BidRequest.User> userMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Source, OpenRtb.BidRequest.Source> sourceMapper;

    @Mock(strictness = LENIENT)
    private ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> regsMapper;

    @BeforeEach
    public void setUp() {
        given(segmentMapper.map(givenSegment())).willReturn(givenProtobufSegment());
        given(formatMapper.map(givenFormat())).willReturn(givenProtobufFormat());
        given(dealMapper.map(givenDeal())).willReturn(givenProtobufDeal());
        given(producerMapper.map(givenProducer())).willReturn(givenProtobufProducer());
        given(dataMapper.map(givenData())).willReturn(givenProtobufData());
        given(contentMapper.map(givenContent())).willReturn(givenProtobufContent());
        given(publisherMapper.map(givenPublisher())).willReturn(givenProtobufPublisher());
        given(siteMapper.map(givenSite())).willReturn(givenProtobufSite());
        given(geoMapper.map(givenGeo())).willReturn(givenProtobufGeo());
        given(bannerMapper.map(givenBanner())).willReturn(givenProtobufBanner());
        given(nativeTitleMapper.map(givenNativeTitle())).willReturn(givenProtobufNativeTitle());
        given(nativeImageMapper.map(givenNativeImage())).willReturn(givenProtobufNativeImage());
        given(nativeVideoMapper.map(givenNativeVideo())).willReturn(givenProtobufNativeVideo());
        given(nativeDataMapper.map(givenNativeData())).willReturn(givenProtobufNativeData());
        given(metricMapper.map(givenMetric())).willReturn(givenProtobufMetric());
        given(videoMapper.map(givenVideo())).willReturn(givenProtobufVideo());
        given(audioMapper.map(givenAudio())).willReturn(givenProtobufAudio());
        given(pmpMapper.map(givenPmp())).willReturn(givenProtobufPmp());
        given(assetMapper.map(givenAssetWithData())).willReturn(givenProtobufNativeAssetWithData());
        given(assetMapper.map(givenAssetWithImage())).willReturn(givenProtobufNativeAssetWithImage());
        given(assetMapper.map(givenAssetWithVideo())).willReturn(givenProtobufNativeAssetWithVideo());
        given(assetMapper.map(givenAssetWithTitle())).willReturn(givenProtobufNativeAssetWithTitle());
        given(eventTrackerMapper.map(givenEventTracker())).willReturn(givenProtobufEventTrackers());
        given(nativeRequestMapper.map(givenNativeRequest())).willReturn(givenProtobufNativeRequest());
        given(stringNativeRequestMapper.map(jacksonMapper.encodeToString(givenNativeRequest())))
                .willReturn(givenProtobufNativeRequest());
        given(nativeMapper.map(givenNativeWithRequestWhichCanBeParsed()))
                .willReturn(givenProtobufNativeWithRequestNative());
        given(impMapper.map(givenImp())).willReturn(givenProtobufImp());
        given(appMapper.map(givenApp())).willReturn(givenProtobufApp());
        given(deviceMapper.map(givenDevice())).willReturn(givenProtobufDevice());
        given(userMapper.map(givenUser())).willReturn(givenProtobufUser());
        given(sourceMapper.map(givenSource())).willReturn(givenProtobufSource());
        given(regsMapper.map(givenRegs())).willReturn(givenProtobufRegs());
    }

    @Test
    public void bidRequestMapperShouldReturnValidMapperForMapperSpec() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final RequestExtensionMappersSpecification spec =
                RequestExtensionMappersSpecification.builder(jacksonMapper.mapper())
                        .segmentExtMapper(givenJsonExtensionMapper(OpenRtbTest.segment))
                        .formatExtMapper(givenJsonExtensionMapper(OpenRtbTest.format))
                        .dealExtMapper(givenJsonExtensionMapper(OpenRtbTest.deal))
                        .producerExtMapper(givenJsonExtensionMapper(OpenRtbTest.producer))
                        .dataExtMapper(givenJsonExtensionMapper(OpenRtbTest.data))
                        .contentExtMapper(givenJsonExtensionMapper(OpenRtbTest.content))
                        .publisherExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.publisher))
                        .siteExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.site))
                        .geoExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.geo))
                        .bannerExtMapper(givenJsonExtensionMapper(OpenRtbTest.banner))
                        .nativeTitleExtMapper(givenJsonExtensionMapper(OpenRtbTest.nativeTitle))
                        .nativeImageExtMapper(givenJsonExtensionMapper(OpenRtbTest.nativeImage))
                        .nativeVideoExtMapper(givenJsonExtensionMapper(OpenRtbTest.video))
                        .nativeDataExtMapper(givenJsonExtensionMapper(OpenRtbTest.nativeData))
                        .metricExtMapper(givenJsonExtensionMapper(OpenRtbTest.metric))
                        .videoExtMapper(givenJsonExtensionMapper(OpenRtbTest.video))
                        .audioExtMapper(givenJsonExtensionMapper(OpenRtbTest.audio))
                        .pmpExtMapper(givenJsonExtensionMapper(OpenRtbTest.pmp))
                        .nativeAssetExtMapper(givenJsonExtensionMapper(OpenRtbTest.nativeAsset))
                        .nativeEventTrackerExtMapper(givenJsonExtensionMapper(OpenRtbTest.eventTrackers))
                        .nativeRequestExtMapper(givenJsonExtensionMapper(OpenRtbTest.nativeRequest))
                        .nativeExtMapper(givenJsonExtensionMapper(OpenRtbTest.native_))
                        .impExtMapper(givenJsonExtensionMapper(OpenRtbTest.imp))
                        .appExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.app))
                        .deviceExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.device))
                        .userExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.user))
                        .sourceExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.source))
                        .regsExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.regs))
                        .bidRequestExtMapper(givenFlexibleExtensionMapper(OpenRtbTest.bidRequest))
                        .build();

        // when
        final ProtobufMapper<BidRequest, OpenRtb.BidRequest> mapper =
                ProtobufRequestUtils.bidRequestMapper(spec);

        // then
        final OpenRtb.BidRequest result = mapper.map(bidRequest);
        final OpenRtb.BidRequest expectedResult = givenProtobufBidRequest();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void bidRequestMapperShouldReturnValidMapper() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final ProtobufMapper<BidRequest, OpenRtb.BidRequest> mapper =
                ProtobufRequestUtils.bidRequestMapper(
                        impMapper,
                        siteMapper,
                        appMapper,
                        deviceMapper,
                        userMapper,
                        sourceMapper,
                        regsMapper,
                        givenFlexibleExtensionMapper(OpenRtbTest.bidRequest));

        // then
        final OpenRtb.BidRequest result = mapper.map(bidRequest);
        final OpenRtb.BidRequest expectedResult = givenProtobufBidRequest();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeVideoMapperShouldReturnValidMapper() {
        // given
        final VideoObject videoObject = givenNativeVideo();

        // when
        final ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> mapper =
                ProtobufRequestUtils.nativeVideoMapper(givenJsonExtensionMapper(OpenRtbTest.video));

        // then
        final OpenRtb.BidRequest.Imp.Video result = mapper.map(videoObject);
        final OpenRtb.BidRequest.Imp.Video expectedResult = givenProtobufNativeVideo();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void producerMapperShouldReturnValidMapper() {
        // given
        final Producer producer = givenProducer();

        // when
        final ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> mapper =
                ProtobufRequestUtils.producerMapper(givenJsonExtensionMapper(OpenRtbTest.producer));

        // then
        final OpenRtb.BidRequest.Producer result = mapper.map(producer);
        final OpenRtb.BidRequest.Producer expectedResult = givenProtobufProducer();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void pmpMapper() {
        // given
        final Pmp pmp = givenPmp();

        // when
        final ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> mapper =
                ProtobufRequestUtils.pmpMapper(dealMapper, givenJsonExtensionMapper(OpenRtbTest.pmp));

        // then
        final OpenRtb.BidRequest.Imp.Pmp result = mapper.map(pmp);
        final OpenRtb.BidRequest.Imp.Pmp expectedResult = givenProtobufPmp();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void siteMapper() {
        // given
        final Site site = givenSite();

        // when
        final ProtobufMapper<Site, OpenRtb.BidRequest.Site> mapper = ProtobufRequestUtils.siteMapper(
                publisherMapper, contentMapper, givenFlexibleExtensionMapper(OpenRtbTest.site));

        // then
        final OpenRtb.BidRequest.Site result = mapper.map(site);
        final OpenRtb.BidRequest.Site expectedResult = givenProtobufSite();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void geoMapperShouldReturnValidMapper() {
        // given
        final Geo geo = givenGeo();

        // when
        final ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> mapper =
                ProtobufRequestUtils.geoMapper(givenFlexibleExtensionMapper(OpenRtbTest.geo));

        // then
        final OpenRtb.BidRequest.Geo result = mapper.map(geo);
        final OpenRtb.BidRequest.Geo expectedResult = givenProtobufGeo();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void bannerMapperShouldReturnValidMapper() {
        // given
        final Banner banner = givenBanner();

        // when
        final ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> mapper =
                ProtobufRequestUtils.bannerMapper(formatMapper, givenJsonExtensionMapper(OpenRtbTest.banner));

        // then
        final OpenRtb.BidRequest.Imp.Banner result = mapper.map(banner);
        final OpenRtb.BidRequest.Imp.Banner expectedResult = givenProtobufBanner();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeImageMapperShouldReturnValidMapper() {
        // when
        final ImageObject imageObject = givenNativeImage();

        // when
        final ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> mapper =
                ProtobufRequestUtils.nativeImageMapper(givenJsonExtensionMapper(OpenRtbTest.nativeImage));

        // then
        final OpenRtb.NativeRequest.Asset.Image result = mapper.map(imageObject);
        final OpenRtb.NativeRequest.Asset.Image expectedResult = givenProtobufNativeImage();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void sourceMapperShouldReturnValidMapper() {
        // given
        final Source source = givenSource();

        // when
        final ProtobufMapper<Source, OpenRtb.BidRequest.Source> mapper =
                ProtobufRequestUtils.sourceMapper(givenFlexibleExtensionMapper(OpenRtbTest.source));

        // then
        final OpenRtb.BidRequest.Source result = mapper.map(source);
        final OpenRtb.BidRequest.Source expectedResult = givenProtobufSource();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void contentMapperShouldReturnValidMapper() {
        // given
        final Content content = givenContent();

        // when
        final ProtobufMapper<Content, OpenRtb.BidRequest.Content> mapper =
                ProtobufRequestUtils.contentMapper(
                        producerMapper, dataMapper, givenJsonExtensionMapper(OpenRtbTest.content));

        // then
        final OpenRtb.BidRequest.Content result = mapper.map(content);
        final OpenRtb.BidRequest.Content expectedResult = givenProtobufContent();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void deviceMapperShouldReturnValidMapper() {
        // given
        final Device device = givenDevice();

        // when
        final ProtobufMapper<Device, OpenRtb.BidRequest.Device> mapper =
                ProtobufRequestUtils.deviceMapper(geoMapper, givenFlexibleExtensionMapper(OpenRtbTest.device));

        // then
        final OpenRtb.BidRequest.Device result = mapper.map(device);
        final OpenRtb.BidRequest.Device expectedResult = givenProtobufDevice();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeDataMapperShouldReturnValidMapper() {
        // given
        final DataObject nativeData = givenNativeData();

        // when
        final ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> mapper =
                ProtobufRequestUtils.nativeDataMapper(givenJsonExtensionMapper(OpenRtbTest.nativeData));

        // then
        final OpenRtb.NativeRequest.Asset.Data result = mapper.map(nativeData);
        final OpenRtb.NativeRequest.Asset.Data expectedResult = givenProtobufNativeData();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void dataMapperShouldReturnValidMapper() {
        // given
        final Data data = givenData();

        // when
        final ProtobufMapper<Data, OpenRtb.BidRequest.Data> mapper =
                ProtobufRequestUtils.dataMapper(segmentMapper, givenJsonExtensionMapper(OpenRtbTest.data));

        // then
        final OpenRtb.BidRequest.Data result = mapper.map(data);
        final OpenRtb.BidRequest.Data expectedResult = givenProtobufData();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void titleMapperShouldReturnValidMapper() {
        // given
        final TitleObject title = givenNativeTitle();

        // when
        final ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> mapper =
                ProtobufRequestUtils.titleMapper(givenJsonExtensionMapper(OpenRtbTest.nativeTitle));

        // then
        final OpenRtb.NativeRequest.Asset.Title result = mapper.map(title);
        final OpenRtb.NativeRequest.Asset.Title expectedResult = givenProtobufNativeTitle();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void metricMapperShouldReturnValidMapper() {
        // given
        final Metric metric = givenMetric();

        // when
        final ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> mapper =
                ProtobufRequestUtils.metricMapper(givenJsonExtensionMapper(OpenRtbTest.metric));

        // then
        final OpenRtb.BidRequest.Imp.Metric result = mapper.map(metric);
        final OpenRtb.BidRequest.Imp.Metric expectedResult = givenProtobufMetric();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void formatMapperShouldReturnValidMapper() {
        // given
        final Format format = givenFormat();

        // when
        final ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> mapper =
                ProtobufRequestUtils.formatMapper(givenJsonExtensionMapper(OpenRtbTest.format));

        // then
        final OpenRtb.BidRequest.Imp.Banner.Format result = mapper.map(format);
        final OpenRtb.BidRequest.Imp.Banner.Format expectedResult = givenProtobufFormat();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void publisherMapperShouldReturnValidMapper() {
        // given
        final Publisher publisher = givenPublisher();

        // when
        final ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> mapper =
                ProtobufRequestUtils.publisherMapper(givenFlexibleExtensionMapper(OpenRtbTest.publisher));

        // then
        final OpenRtb.BidRequest.Publisher result = mapper.map(publisher);
        final OpenRtb.BidRequest.Publisher expectedResult = givenProtobufPublisher();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void dealMapperShouldReturnValidMapper() {
        // given
        final Deal deal = givenDeal();

        // when
        final ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> mapper =
                ProtobufRequestUtils.dealMapper(givenJsonExtensionMapper(OpenRtbTest.deal));

        // then
        final OpenRtb.BidRequest.Imp.Pmp.Deal result = mapper.map(deal);
        final OpenRtb.BidRequest.Imp.Pmp.Deal expectedResult = givenProtobufDeal();
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void eventTrackerMapperShouldReturnValidMapper() {
        // given
        final EventTracker eventTracker = givenEventTracker();

        // when
        final ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> mapper =
                ProtobufRequestUtils.eventTrackerMapper(givenJsonExtensionMapper(OpenRtbTest.eventTrackers));

        // then
        final OpenRtb.NativeRequest.EventTrackers result = mapper.map(eventTracker);
        final OpenRtb.NativeRequest.EventTrackers expectedResult = givenProtobufEventTrackers();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void regsMapperShouldReturnValidMapper() {
        // given
        final Regs regs = givenRegs();

        // when
        final ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> mapper =
                ProtobufRequestUtils.regsMapper(givenFlexibleExtensionMapper(OpenRtbTest.regs));

        // then
        final OpenRtb.BidRequest.Regs result = mapper.map(regs);
        final OpenRtb.BidRequest.Regs expectedResult = givenProtobufRegs();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void appMapperShouldReturnValidMapper() {
        // given
        final App app = givenApp();

        // when
        final ProtobufMapper<App, OpenRtb.BidRequest.App> mapper = ProtobufRequestUtils.appMapper(
                publisherMapper, contentMapper, givenFlexibleExtensionMapper(OpenRtbTest.app));

        // then
        final OpenRtb.BidRequest.App result = mapper.map(app);
        final OpenRtb.BidRequest.App expectedResult = givenProtobufApp();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void audioMapperShouldReturnValidMapper() {
        // given
        final Audio audio = givenAudio();

        // when
        final ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> mapper =
                ProtobufRequestUtils.audioMapper(bannerMapper, givenJsonExtensionMapper(OpenRtbTest.audio));

        // then
        final OpenRtb.BidRequest.Imp.Audio result = mapper.map(audio);
        final OpenRtb.BidRequest.Imp.Audio expectedResult = givenProtobufAudio();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeRequestMapperShouldReturnValidMapper() {
        // given
        final Request nativeRequest = givenNativeRequest();

        // when
        final ProtobufMapper<Request, OpenRtb.NativeRequest> mapper = ProtobufRequestUtils.nativeRequestMapper(
                assetMapper, eventTrackerMapper, givenJsonExtensionMapper(OpenRtbTest.nativeRequest));

        // then
        final OpenRtb.NativeRequest result = mapper.map(nativeRequest);
        final OpenRtb.NativeRequest expectedResult = givenProtobufNativeRequest();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void stringNativeRequestMapperShouldReturnValidMapper() {
        // given
        final String request = jacksonMapper.encodeToString(givenNativeRequest());

        // when
        final ProtobufMapper<String, OpenRtb.NativeRequest> mapper =
                ProtobufRequestUtils.nativeRequestMapper(jacksonMapper.mapper(), nativeRequestMapper);

        // then
        final OpenRtb.NativeRequest result = mapper.map(request);
        final OpenRtb.NativeRequest expectedResult = givenProtobufNativeRequest();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeMapperShouldReturnValidMapperForRequestWhichCantBeParsed() {
        // given
        final Native xNative = givenNativeWithRequestWhichCantBeParsed();

        // when
        final ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> mapper = ProtobufRequestUtils.nativeMapper(
                stringNativeRequestMapper, givenJsonExtensionMapper(OpenRtbTest.native_));

        // then
        final OpenRtb.BidRequest.Imp.Native result = mapper.map(xNative);
        final OpenRtb.BidRequest.Imp.Native expectedResult = givenProtobufNativeWithRequest();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeMapperShouldReturnValidMapperForRequestWhichCanBeParsed() {
        // given
        final Native xNative = givenNativeWithRequestWhichCanBeParsed();
        given(stringNativeRequestMapper.map(eq("request"))).willReturn(null);

        // when
        final ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> mapper = ProtobufRequestUtils.nativeMapper(
                stringNativeRequestMapper, givenJsonExtensionMapper(OpenRtbTest.native_));

        // then
        final OpenRtb.BidRequest.Imp.Native result = mapper.map(xNative);
        final OpenRtb.BidRequest.Imp.Native expectedResult = givenProtobufNativeWithRequestNative();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsTitle() {
        // given
        final Asset asset = givenAssetWithTitle();

        // when
        final ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> mapper =
                ProtobufRequestUtils.assetMapper(
                        nativeTitleMapper,
                        nativeImageMapper,
                        nativeVideoMapper,
                        nativeDataMapper,
                        givenJsonExtensionMapper(OpenRtbTest.nativeAsset));

        // then
        final OpenRtb.NativeRequest.Asset result = mapper.map(asset);
        final OpenRtb.NativeRequest.Asset expectedResult = givenProtobufNativeAssetWithTitle();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsVideo() {
        // given
        final Asset asset = givenAssetWithVideo();

        // when
        final ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> mapper =
                ProtobufRequestUtils.assetMapper(
                        nativeTitleMapper,
                        nativeImageMapper,
                        nativeVideoMapper,
                        nativeDataMapper,
                        givenJsonExtensionMapper(OpenRtbTest.nativeAsset));

        // then
        final OpenRtb.NativeRequest.Asset result = mapper.map(asset);
        final OpenRtb.NativeRequest.Asset expectedResult = givenProtobufNativeAssetWithVideo();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsImage() {
        // given
        final Asset asset = givenAssetWithImage();

        // when
        final ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> mapper =
                ProtobufRequestUtils.assetMapper(
                        nativeTitleMapper,
                        nativeImageMapper,
                        nativeVideoMapper,
                        nativeDataMapper,
                        givenJsonExtensionMapper(OpenRtbTest.nativeAsset));

        // then
        final OpenRtb.NativeRequest.Asset result = mapper.map(asset);
        final OpenRtb.NativeRequest.Asset expectedResult = givenProtobufNativeAssetWithImage();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void assetMapperShouldReturnValidMapperWhichMapsData() {
        // given
        final Asset asset = givenAssetWithData();

        // when
        final ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> mapper =
                ProtobufRequestUtils.assetMapper(
                        nativeTitleMapper,
                        nativeImageMapper,
                        nativeVideoMapper,
                        nativeDataMapper,
                        givenJsonExtensionMapper(OpenRtbTest.nativeAsset));

        // then
        final OpenRtb.NativeRequest.Asset result = mapper.map(asset);
        final OpenRtb.NativeRequest.Asset expectedResult = givenProtobufNativeAssetWithData();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void segmentMapperShouldReturnValidMapper() {
        // given
        final Segment segment = givenSegment();

        // when
        final ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> mapper =
                ProtobufRequestUtils.segmentMapper(givenJsonExtensionMapper(OpenRtbTest.segment));

        // then
        final OpenRtb.BidRequest.Data.Segment result = mapper.map(segment);
        final OpenRtb.BidRequest.Data.Segment expectedResult =
                OpenRtb.BidRequest.Data.Segment.newBuilder()
                        .setId("id")
                        .setName("name")
                        .setValue("value")
                        .setExtension(OpenRtbTest.segment, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void impMapperShouldReturnValidMapper() {
        // given
        final Imp imp = givenImp();

        // when
        final ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> mapper =
                ProtobufRequestUtils.impMapper(
                        metricMapper,
                        bannerMapper,
                        videoMapper,
                        audioMapper,
                        nativeMapper,
                        pmpMapper,
                        givenJsonExtensionMapper(OpenRtbTest.imp));

        // then
        final OpenRtb.BidRequest.Imp result = mapper.map(imp);
        final OpenRtb.BidRequest.Imp expectedResult = givenProtobufImp();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void videoMapperShouldReturnValidMapper() {
        // given
        final Video video = givenVideo();

        // when
        final ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> mapper =
                ProtobufRequestUtils.videoMapper(bannerMapper, givenJsonExtensionMapper(OpenRtbTest.video));

        // then
        final OpenRtb.BidRequest.Imp.Video result = mapper.map(video);
        final OpenRtb.BidRequest.Imp.Video expectedResult = givenProtobufVideo();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void userMapperShouldReturnValidMapper() {
        // given
        final User user = givenUser();

        // when
        final ProtobufMapper<User, OpenRtb.BidRequest.User> mapper = ProtobufRequestUtils.userMapper(
                geoMapper, dataMapper, givenFlexibleExtensionMapper(OpenRtbTest.user));

        // then
        final OpenRtb.BidRequest.User result = mapper.map(user);
        final OpenRtb.BidRequest.User expectedResult = givenProtobufUser();

        assertThat(result).isEqualTo(expectedResult);
    }

    private static BidRequest givenBidRequest() {
        final ExtRequest extRequest = ExtRequest.of(null);
        extRequest.addProperty("field", TextNode.valueOf("fieldValue"));

        return BidRequest.builder()
                .id("id")
                .imp(singletonList(givenImp()))
                .site(givenSite())
                .app(givenApp())
                .device(givenDevice())
                .user(givenUser())
                .test(1)
                .at(2)
                .tmax(3L)
                .wseat(singletonList("wseat"))
                .bseat(singletonList("bseat"))
                .allimps(4)
                .cur(singletonList("cur"))
                .wlang(singletonList("wlang"))
                .bcat(singletonList("bcat"))
                .badv(singletonList("badv"))
                .bapp(singletonList("bapp"))
                .source(givenSource())
                .regs(givenRegs())
                .ext(extRequest)
                .build();
    }

    private static OpenRtb.BidRequest givenProtobufBidRequest() {
        return OpenRtb.BidRequest.newBuilder()
                .setId("id")
                .addImp(givenProtobufImp())
                .setSite(givenProtobufSite())
                .setApp(givenProtobufApp())
                .setDevice(givenProtobufDevice())
                .setUser(givenProtobufUser())
                .setTest(true)
                .setAt(2)
                .setTmax(3)
                .addWseat("wseat")
                .addBseat("bseat")
                .setAllimps(true)
                .addCur("cur")
                .addWlang("wlang")
                .addBcat("bcat")
                .addBadv("badv")
                .addBapp("bapp")
                .setSource(givenProtobufSource())
                .setRegs(givenProtobufRegs())
                .setExtension(OpenRtbTest.bidRequest, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Regs givenRegs() {
        final ExtRegs extRegs = ExtRegs.of(0, "", null, null);
        extRegs.addProperty("field", TextNode.valueOf("fieldValue"));

        return Regs.builder()
                .coppa(1)
                .ext(extRegs)
                .build();
    }

    private static OpenRtb.BidRequest.Regs givenProtobufRegs() {
        return OpenRtb.BidRequest.Regs.newBuilder()
                .setCoppa(true)
                .setExtension(OpenRtbTest.regs, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Imp givenImp() {
        return Imp.builder()
                .id("id")
                .metric(singletonList(givenMetric()))
                .banner(givenBanner())
                .video(givenVideo())
                .audio(givenAudio())
                .xNative(givenNativeWithRequestWhichCanBeParsed())
                .pmp(givenPmp())
                .displaymanager("displaymanager")
                .displaymanagerver("displaymanagerver")
                .instl(1)
                .tagid("tagid")
                .bidfloor(BigDecimal.ONE)
                .bidfloorcur("bidfloorcur")
                .clickbrowser(2)
                .secure(3)
                .iframebuster(singletonList("iframebuster"))
                .exp(5)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp givenProtobufImp() {
        return OpenRtb.BidRequest.Imp.newBuilder()
                .setId("id")
                .addMetric(givenProtobufMetric())
                .setBanner(givenProtobufBanner())
                .setVideo(givenProtobufVideo())
                .setAudio(givenProtobufAudio())
                .setNative(givenProtobufNativeWithRequestNative())
                .setPmp(givenProtobufPmp())
                .setDisplaymanager("displaymanager")
                .setDisplaymanagerver("displaymanagerver")
                .setInstl(true)
                .setTagid("tagid")
                .setBidfloor(1.0)
                .setBidfloorcur("bidfloorcur")
                .setClickbrowser(true)
                .setSecure(true)
                .addIframebuster("iframebuster")
                .setExp(5)
                .setExtension(OpenRtbTest.imp, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Native givenNativeWithRequestWhichCanBeParsed() {
        return Native.builder()
                .request(jacksonMapper.encodeToString(givenNativeRequest()))
                .ver("ver")
                .battr(singletonList(1))
                .api(singletonList(2))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static Native givenNativeWithRequestWhichCantBeParsed() {
        return givenNativeWithRequestWhichCanBeParsed().toBuilder().request("request").build();
    }

    private static OpenRtb.BidRequest.Imp.Native givenProtobufNativeWithRequest() {
        return OpenRtb.BidRequest.Imp.Native.newBuilder()
                .setRequest("request")
                .setVer("ver")
                .addBattr(1)
                .addApi(2)
                .setExtension(OpenRtbTest.native_, givenProtobufExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Native givenProtobufNativeWithRequestNative() {
        return OpenRtb.BidRequest.Imp.Native.newBuilder(givenProtobufNativeWithRequest())
                .setRequest("")
                .setRequestNative(givenProtobufNativeRequest())
                .build();
    }

    private static Request givenNativeRequest() {
        return Request.builder()
                .assets(singletonList(givenAssetWithData()))
                .context(1)
                .contextsubtype(2)
                .plcmttype(3)
                .plcmtcnt(4)
                .seq(5)
                .privacy(6)
                .aurlsupport(7)
                .durlsupport(8)
                .ver("ver")
                .eventtrackers(singletonList(givenEventTracker()))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeRequest givenProtobufNativeRequest() {
        return OpenRtb.NativeRequest.newBuilder()
                .setVer("ver")
                .setContext(1)
                .setContextsubtype(2)
                .setPlcmttype(3)
                .setPlcmtcnt(4)
                .setSeq(5)
                .addAssets(givenProtobufNativeAssetWithData())
                .setAurlsupport(true)
                .setDurlsupport(true)
                .addEventtrackers(givenProtobufEventTrackers())
                .setPrivacy(true)
                .setExtension(OpenRtbTest.nativeRequest, givenProtobufExt("fieldValue"))
                .build();
    }

    private static EventTracker givenEventTracker() {
        return EventTracker.builder()
                .event(1)
                .methods(List.of(2, 3))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeRequest.EventTrackers givenProtobufEventTrackers() {
        return OpenRtb.NativeRequest.EventTrackers.newBuilder()
                .setEvent(1)
                .addAllMethods(List.of(2, 3))
                .setExtension(OpenRtbTest.eventTrackers, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Metric givenMetric() {
        return Metric.builder()
                .type("type")
                .value(1.0f)
                .vendor("vendor")
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Metric givenProtobufMetric() {
        return OpenRtb.BidRequest.Imp.Metric.newBuilder()
                .setType("type")
                .setValue(1.0)
                .setVendor("vendor")
                .setExtension(OpenRtbTest.metric, givenProtobufExt("fieldValue"))
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
        final Asset.AssetBuilder builder = Asset.builder()
                .id(1)
                .required(2)
                .ext(givenJsonExt("fieldValue"));

        return assetModifier.apply(builder).build();
    }

    private static OpenRtb.NativeRequest.Asset givenProtobufNativeAssetWithVideo() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setVideo(givenProtobufNativeVideo()));
    }

    private static OpenRtb.NativeRequest.Asset givenProtobufNativeAssetWithImage() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setImg(givenProtobufNativeImage()));
    }

    private static OpenRtb.NativeRequest.Asset givenProtobufNativeAssetWithData() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setData(givenProtobufNativeData()));
    }

    private static OpenRtb.NativeRequest.Asset givenProtobufNativeAssetWithTitle() {
        return givenProtobufNativeAsset(assetBuilder -> assetBuilder.setTitle(givenProtobufNativeTitle()));
    }

    private static OpenRtb.NativeRequest.Asset givenProtobufNativeAsset(
            UnaryOperator<OpenRtb.NativeRequest.Asset.Builder> assetModifier) {

        final OpenRtb.NativeRequest.Asset.Builder assetBuilder = OpenRtb.NativeRequest.Asset.newBuilder()
                .setId(1)
                .setRequired(true)
                .setExtension(OpenRtbTest.nativeAsset, givenProtobufExt("fieldValue"));

        return assetModifier.apply(assetBuilder).build();
    }

    private static ImageObject givenNativeImage() {
        return ImageObject.builder()
                .type(1)
                .w(2)
                .h(3)
                .wmin(4)
                .hmin(5)
                .mimes(singletonList("mimes"))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeRequest.Asset.Image givenProtobufNativeImage() {
        return OpenRtb.NativeRequest.Asset.Image.newBuilder()
                .setType(1)
                .setW(2)
                .setH(3)
                .setWmin(4)
                .setHmin(5)
                .addMimes("mimes")
                .setExtension(OpenRtbTest.nativeImage, givenProtobufExt("fieldValue"))
                .build();
    }

    private static TitleObject givenNativeTitle() {
        return TitleObject.builder()
                .len(1)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeRequest.Asset.Title givenProtobufNativeTitle() {
        return OpenRtb.NativeRequest.Asset.Title.newBuilder()
                .setLen(1)
                .setExtension(OpenRtbTest.nativeTitle, givenProtobufExt("fieldValue"))
                .build();
    }

    private static VideoObject givenNativeVideo() {
        return VideoObject.builder()
                .protocols(singletonList(1))
                .mimes(singletonList("mimes"))
                .minduration(2)
                .maxduration(3)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Video givenProtobufNativeVideo() {
        return OpenRtb.BidRequest.Imp.Video.newBuilder()
                .addProtocols(1)
                .addMimes("mimes")
                .setMinduration(2)
                .setMaxduration(3)
                .setExtension(OpenRtbTest.video, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Video givenVideo() {
        return Video.builder()
                .mimes(singletonList("mimes"))
                .minduration(1)
                .maxduration(2)
                .startdelay(3)
                .protocols(singletonList(4))
                .w(5)
                .h(6)
                .placement(7)
                .linearity(8)
                .skip(9)
                .skipmin(10)
                .skipafter(11)
                .sequence(12)
                .battr(singletonList(13))
                .maxextended(14)
                .minbitrate(15)
                .maxbitrate(16)
                .boxingallowed(17)
                .playbackmethod(singletonList(18))
                .playbackend(19)
                .delivery(singletonList(20))
                .pos(21)
                .companionad(singletonList(givenBanner()))
                .api(singletonList(22))
                .companiontype(singletonList(23))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Video givenProtobufVideo() {
        return OpenRtb.BidRequest.Imp.Video.newBuilder()
                .addMimes("mimes")
                .setMinduration(1)
                .setMaxduration(2)
                .setStartdelay(3)
                .addProtocols(4)
                .setW(5)
                .setH(6)
                .setPlacement(7)
                .setLinearity(8)
                .setSkip(true)
                .setSkipmin(10)
                .setSkipafter(11)
                .setSequence(12)
                .addBattr(13)
                .setMaxextended(14)
                .setMinbitrate(15)
                .setMaxbitrate(16)
                .setBoxingallowed(true)
                .addPlaybackmethod(18)
                .setPlaybackend(19)
                .addDelivery(20)
                .setPos(21)
                .addCompanionad(givenProtobufBanner())
                .addApi(22)
                .addCompaniontype(23)
                .setExtension(OpenRtbTest.video, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Audio givenAudio() {
        return Audio.builder()
                .mimes(singletonList("mimes"))
                .minduration(1)
                .maxduration(2)
                .protocols(singletonList(3))
                .startdelay(4)
                .sequence(5)
                .battr(singletonList(6))
                .maxextended(7)
                .minbitrate(8)
                .maxbitrate(9)
                .delivery(singletonList(10))
                .companionad(singletonList(givenBanner()))
                .api(singletonList(11))
                .companiontype(singletonList(12))
                .maxseq(13)
                .feed(14)
                .stitched(15)
                .nvol(16)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Audio givenProtobufAudio() {
        return OpenRtb.BidRequest.Imp.Audio.newBuilder()
                .addMimes("mimes")
                .setMinduration(1)
                .setMaxduration(2)
                .addProtocols(3)
                .setStartdelay(4)
                .setSequence(5)
                .addBattr(6)
                .setMaxextended(7)
                .setMinbitrate(8)
                .setMaxbitrate(9)
                .addDelivery(10)
                .addCompanionad(givenProtobufBanner())
                .addApi(11)
                .addCompaniontype(12)
                .setMaxseq(13)
                .setFeed(14)
                .setStitched(true)
                .setNvol(16)
                .setExtension(OpenRtbTest.audio, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Device givenDevice() {
        final ExtDevice extDevice = ExtDevice.of(null, null, null);
        extDevice.addProperty("field", TextNode.valueOf("fieldValue"));

        return Device.builder()
                .geo(givenGeo())
                .dnt(1)
                .lmt(2)
                .ua("ua")
                .ip("ip")
                .ipv6("ipv6")
                .devicetype(3)
                .make("make")
                .model("model")
                .os("os")
                .osv("osv")
                .hwv("hwv")
                .h(4)
                .w(5)
                .ppi(6)
                .pxratio(BigDecimal.ONE)
                .js(7)
                .geofetch(8)
                .flashver("flashver")
                .language("language")
                .carrier("carrier")
                .mccmnc("mccmnc")
                .connectiontype(9)
                .ifa("ifa")
                .didsha1("didsha1")
                .didmd5("didmd5")
                .dpidsha1("dpidsha1")
                .dpidmd5("dpidmd5")
                .macsha1("macsha1")
                .macmd5("macmd5")
                .ext(extDevice)
                .build();
    }

    private static OpenRtb.BidRequest.Device givenProtobufDevice() {
        return OpenRtb.BidRequest.Device.newBuilder()
                .setGeo(givenProtobufGeo())
                .setDnt(true)
                .setLmt(true)
                .setUa("ua")
                .setIp("ip")
                .setIpv6("ipv6")
                .setDevicetype(3)
                .setMake("make")
                .setModel("model")
                .setOs("os")
                .setOsv("osv")
                .setHwv("hwv")
                .setH(4)
                .setW(5)
                .setPpi(6)
                .setPxratio(1.0)
                .setJs(true)
                .setGeofetch(true)
                .setFlashver("flashver")
                .setLanguage("language")
                .setCarrier("carrier")
                .setMccmnc("mccmnc")
                .setConnectiontype(9)
                .setIfa("ifa")
                .setDidsha1("didsha1")
                .setDidmd5("didmd5")
                .setDpidsha1("dpidsha1")
                .setDpidmd5("dpidmd5")
                .setMacsha1("macsha1")
                .setMacmd5("macmd5")
                .setExtension(OpenRtbTest.device, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Banner givenBanner() {
        return Banner.builder()
                .id("id")
                .format(singletonList(givenFormat()))
                .w(1)
                .h(2)
                .btype(singletonList(3))
                .battr(singletonList(4))
                .pos(5)
                .mimes(singletonList("mimes"))
                .topframe(6)
                .expdir(singletonList(7))
                .api(singletonList(8))
                .vcm(9)
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Banner givenProtobufBanner() {
        return OpenRtb.BidRequest.Imp.Banner.newBuilder()
                .setId("id")
                .addAllFormat(singletonList(givenProtobufFormat()))
                .setW(1)
                .setH(2)
                .addBtype(3)
                .addBattr(4)
                .setPos(5)
                .addMimes("mimes")
                .setTopframe(true)
                .addExpdir(7)
                .addApi(8)
                .setVcm(true)
                .build();
    }

    private static App givenApp() {
        final ExtApp extApp = ExtApp.of(null, null);
        extApp.addProperty("field", TextNode.valueOf("fieldValue"));

        return App.builder()
                .id("id")
                .name("name")
                .bundle("bundle")
                .domain("domain")
                .storeurl("storeurl")
                .cat(singletonList("cat"))
                .sectioncat(singletonList("sectioncat"))
                .pagecat(singletonList("pagecat"))
                .ver("ver")
                .privacypolicy(1)
                .paid(2)
                .publisher(givenPublisher())
                .content(givenContent())
                .keywords("keywords")
                .ext(extApp)
                .build();
    }

    private static OpenRtb.BidRequest.App givenProtobufApp() {
        return OpenRtb.BidRequest.App.newBuilder()
                .setId("id")
                .setName("name")
                .setBundle("bundle")
                .setDomain("domain")
                .setStoreurl("storeurl")
                .addCat("cat")
                .addSectioncat("sectioncat")
                .addPagecat("pagecat")
                .setVer("ver")
                .setPrivacypolicy(true)
                .setPaid(true)
                .setPublisher(givenProtobufPublisher())
                .setContent(givenProtobufContent())
                .setKeywords("keywords")
                .setExtension(OpenRtbTest.app, givenProtobufExt("fieldValue"))
                .build();
    }

    private static User givenUser() {
        final ExtUser extUser = ExtUser.builder().build();
        extUser.addProperty("field", TextNode.valueOf("fieldValue"));

        return User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .keywords("keywords")
                .customdata("customdata")
                .geo(givenGeo())
                .data(singletonList(givenData()))
                .ext(extUser)
                .build();
    }

    private static OpenRtb.BidRequest.User givenProtobufUser() {
        return OpenRtb.BidRequest.User.newBuilder()
                .setId("id")
                .setBuyeruid("buyeruid")
                .setYob(1)
                .setGender("gender")
                .setKeywords("keywords")
                .setCustomdata("customdata")
                .setGeo(givenProtobufGeo())
                .addData(givenProtobufData())
                .setExtension(OpenRtbTest.user, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Geo givenGeo() {
        final ExtGeo extGeo = ExtGeo.of();
        extGeo.addProperty("field", TextNode.valueOf("fieldValue"));

        return Geo.builder()
                .type(1)
                .country("country")
                .metro("metro")
                .region("region")
                .city("city")
                .zip("zip")
                .lat(1.0f)
                .lon(2.0f)
                .accuracy(2)
                .ipservice(3)
                .lastfix(4)
                .regionfips104("regionfips104")
                .utcoffset(5)
                .ext(extGeo)
                .build();
    }

    private static OpenRtb.BidRequest.Geo givenProtobufGeo() {
        return OpenRtb.BidRequest.Geo.newBuilder()
                .setType(1)
                .setCountry("country")
                .setMetro("metro")
                .setRegion("region")
                .setCity("city")
                .setZip("zip")
                .setLat(1.0f)
                .setLon(2.0f)
                .setAccuracy(2)
                .setIpservice(3)
                .setLastfix(4)
                .setRegionfips104("regionfips104")
                .setUtcoffset(5)
                .setExtension(OpenRtbTest.geo, givenProtobufExt("fieldValue"))
                .build();
    }

    private static DataObject givenNativeData() {
        return DataObject.builder()
                .type(1)
                .len(2)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.NativeRequest.Asset.Data givenProtobufNativeData() {
        return OpenRtb.NativeRequest.Asset.Data.newBuilder()
                .setType(1)
                .setLen(2)
                .setExtension(OpenRtbTest.nativeData, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Source givenSource() {
        final ExtSource extSource = ExtSource.of(null);
        extSource.addProperty("field", TextNode.valueOf("fieldValue"));

        return Source.builder()
                .tid("tid")
                .pchain("pchain")
                .fd(1)
                .ext(extSource)
                .build();
    }

    private static OpenRtb.BidRequest.Source givenProtobufSource() {
        return OpenRtb.BidRequest.Source.newBuilder()
                .setTid("tid")
                .setPchain("pchain")
                .setFd(true)
                .setExtension(OpenRtbTest.source, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Pmp givenPmp() {
        return Pmp.builder()
                .deals(singletonList(givenDeal()))
                .privateAuction(1)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Pmp givenProtobufPmp() {
        return OpenRtb.BidRequest.Imp.Pmp.newBuilder()
                .addAllDeals(singletonList(givenProtobufDeal()))
                .setPrivateAuction(true)
                .setExtension(OpenRtbTest.pmp, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Site givenSite() {
        final ExtSite extSite = ExtSite.of(null, null);
        extSite.addProperty("field", TextNode.valueOf("fieldValue"));

        return Site.builder()
                .id("id")
                .name("name")
                .domain("domain")
                .cat(singletonList("cat"))
                .sectioncat(singletonList("sectioncat"))
                .pagecat(singletonList("pagecat"))
                .page("page")
                .ref("ref")
                .search("search")
                .mobile(1)
                .privacypolicy(2)
                .publisher(givenPublisher())
                .content(givenContent())
                .keywords("keywords")
                .ext(extSite)
                .build();
    }

    private static OpenRtb.BidRequest.Site givenProtobufSite() {
        return OpenRtb.BidRequest.Site.newBuilder()
                .setId("id")
                .setName("name")
                .setDomain("domain")
                .addCat("cat")
                .addSectioncat("sectioncat")
                .addPagecat("pagecat")
                .setPage("page")
                .setRef("ref")
                .setSearch("search")
                .setMobile(true)
                .setPrivacypolicy(true)
                .setPublisher(givenProtobufPublisher())
                .setContent(givenProtobufContent())
                .setKeywords("keywords")
                .setExtension(OpenRtbTest.site, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Publisher givenPublisher() {
        final ExtPublisher extPublisher = ExtPublisher.empty();
        extPublisher.addProperty("field", TextNode.valueOf("fieldValue"));

        return Publisher.builder()
                .id("id")
                .domain("domain")
                .cat(singletonList("cat"))
                .name("name")
                .ext(extPublisher)
                .build();
    }

    private static OpenRtb.BidRequest.Publisher givenProtobufPublisher() {
        return OpenRtb.BidRequest.Publisher.newBuilder()
                .setId("id")
                .setDomain("domain")
                .addCat("cat")
                .setName("name")
                .setExtension(OpenRtbTest.publisher, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Content givenContent() {
        return Content.builder()
                .id("id")
                .episode(1)
                .title("title")
                .series("series")
                .season("season")
                .artist("artist")
                .genre("genre")
                .album("album")
                .isrc("isrc")
                .producer(givenProducer())
                .url("url")
                .cat(singletonList("cat"))
                .prodq(2)
                .context(3)
                .contentrating("contentrating")
                .userrating("userrating")
                .qagmediarating(4)
                .keywords("keywords")
                .livestream(5)
                .sourcerelationship(6)
                .len(7)
                .language("language")
                .embeddable(8)
                .data(singletonList(givenData()))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Content givenProtobufContent() {
        return OpenRtb.BidRequest.Content.newBuilder()
                .setId("id")
                .setEpisode(1)
                .setTitle("title")
                .setSeries("series")
                .setSeason("season")
                .setArtist("artist")
                .setGenre("genre")
                .setAlbum("album")
                .setIsrc("isrc")
                .setProducer(givenProtobufProducer())
                .setUrl("url")
                .addCat("cat")
                .setProdq(2)
                .setContext(3)
                .setContentrating("contentrating")
                .setUserrating("userrating")
                .setQagmediarating(4)
                .setKeywords("keywords")
                .setLivestream(true)
                .setSourcerelationship(true)
                .setLen(7)
                .setLanguage("language")
                .setEmbeddable(true)
                .addData(givenProtobufData())
                .setExtension(OpenRtbTest.content, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Data givenData() {
        return Data.builder()
                .id("id")
                .name("name")
                .segment(singletonList(givenSegment()))
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Data givenProtobufData() {
        return OpenRtb.BidRequest.Data.newBuilder()
                .setId("id")
                .setName("name")
                .addSegment(givenProtobufSegment())
                .setExtension(OpenRtbTest.data, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Producer givenProducer() {
        return Producer.builder()
                .id("id")
                .name("name")
                .cat(singletonList("cat"))
                .domain("domain")
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Producer givenProtobufProducer() {
        return OpenRtb.BidRequest.Producer.newBuilder()
                .setId("id")
                .setName("name")
                .addCat("cat")
                .setDomain("domain")
                .setExtension(OpenRtbTest.producer, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Deal givenDeal() {
        return Deal.builder()
                .id("id")
                .at(1)
                .wseat(singletonList("wseat"))
                .wadomain(singletonList("wadomain"))
                .bidfloor(BigDecimal.ONE)
                .bidfloorcur("bidfloorcur")
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Pmp.Deal givenProtobufDeal() {
        return OpenRtb.BidRequest.Imp.Pmp.Deal.newBuilder()
                .setId("id")
                .setAt(1)
                .addWseat("wseat")
                .addWadomain("wadomain")
                .setBidfloor(1)
                .setBidfloorcur("bidfloorcur")
                .setExtension(OpenRtbTest.deal, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Format givenFormat() {
        return Format.builder()
                .w(1)
                .h(2)
                .wratio(3)
                .hratio(4)
                .wmin(5)
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Imp.Banner.Format givenProtobufFormat() {
        return OpenRtb.BidRequest.Imp.Banner.Format.newBuilder()
                .setW(1)
                .setH(2)
                .setWratio(3)
                .setHratio(4)
                .setWmin(5)
                .setExtension(OpenRtbTest.format, givenProtobufExt("fieldValue"))
                .build();
    }

    private static Segment givenSegment() {
        return Segment.builder()
                .id("id")
                .name("name")
                .value("value")
                .ext(givenJsonExt("fieldValue"))
                .build();
    }

    private static OpenRtb.BidRequest.Data.Segment givenProtobufSegment() {
        return OpenRtb.BidRequest.Data.Segment.newBuilder()
                .setId("id")
                .setName("name")
                .setValue("value")
                .setExtension(OpenRtbTest.segment, givenProtobufExt("fieldValue"))
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

    private static <ContainingType extends Message, FromType extends FlexibleExtension>
            ProtobufForwardExtensionMapper<ContainingType, FromType, OpenRtbTest.TestExt> givenFlexibleExtensionMapper(
            Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor) {

        return givenExtensionMapper(ext -> givenProtobufExt(ext.getProperty("field").asText()), extensionDescriptor);
    }

    private static <ContainingType extends Message, FromType>
            ProtobufForwardExtensionMapper<ContainingType, FromType, OpenRtbTest.TestExt> givenExtensionMapper(
            Function<FromType, OpenRtbTest.TestExt> mapper,
            Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor) {

        return new ProtobufForwardExtensionMapper<>() {
            @Override
            public OpenRtbTest.TestExt map(FromType ext) {
                return mapper.apply(ext);
            }

            @Override
            public Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor() {
                return extensionDescriptor;
            }
        };
    }

    private static <ContainingType extends Message> JsonProtobufExtensionMapper<ContainingType, OpenRtbTest.TestExt>
            givenJsonExtensionMapper(Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor) {

        return new JsonProtobufExtensionMapper<>() {

            @Override
            public OpenRtbTest.TestExt map(ObjectNode ext) {
                assert ext != null;

                final JsonNode fieldNode = ext.get("field");
                assert fieldNode.isTextual();
                final TextNode fieldNodeAsText = (TextNode) fieldNode;
                return OpenRtbTest.TestExt.newBuilder()
                        .setField(fieldNodeAsText.textValue())
                        .build();
            }

            @Override
            public Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor() {
                return extensionDescriptor;
            }
        };
    }
}
