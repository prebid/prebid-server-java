package org.prebid.server.protobuf.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.protobuf.Extension;
import com.google.protobuf.Message;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.EventTracker;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Producer;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.TitleObject;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.openrtb.v2.OpenRtbTest;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class ProtobufRequestUtilsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> segmentMapper;

    @Mock
    private ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> formatMapper;

    private ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Geo, ExtGeo, OpenRtbTest.TestExt> geoExtMapper;

    @Before
    public void setUp() {
        given(segmentMapper.map(any())).willReturn(givenProtobufSegment());
        given(formatMapper.map(any())).willReturn(givenProtobufFormat());

        geoExtMapper = givenExtensionMapper(
                ext -> givenProtobufExt(ext.getProperty("field").asText()), OpenRtbTest.geo);

    }

    @Test
    public void bidRequestMapperShouldReturnValidMapper() {
//        final BidRequest bidRequest = BidRequest.builder()
//                        .id("id")
//                                .imp(Collections.si)
//        setNotNull(bidRequest.getId(), resultBuilder::setId);
//        setNotNull(mapList(bidRequest.getImp(), impMapper::map), resultBuilder::addAllImp);
//        setNotNull(mapNotNull(bidRequest.getSite(), siteMapper::map), resultBuilder::setSite);
//        setNotNull(mapNotNull(bidRequest.getApp(), appMapper::map), resultBuilder::setApp);
//        setNotNull(mapNotNull(bidRequest.getDevice(), deviceMapper::map), resultBuilder::setDevice);
//        setNotNull(mapNotNull(bidRequest.getUser(), userMapper::map), resultBuilder::setUser);
//        setNotNull(mapNotNull(bidRequest.getTest(), BooleanUtils::toBoolean), resultBuilder::setTest);
//        setNotNull(bidRequest.getAt(), resultBuilder::setAt);
//        setNotNull(mapNotNull(bidRequest.getTmax(), Long::intValue), resultBuilder::setTmax);
//        setNotNull(bidRequest.getWseat(), resultBuilder::addAllWseat);
//        setNotNull(bidRequest.getBseat(), resultBuilder::addAllBseat);
//        setNotNull(mapNotNull(bidRequest.getAllimps(), BooleanUtils::toBoolean), resultBuilder::setAllimps);
//        setNotNull(bidRequest.getCur(), resultBuilder::addAllCur);
//        setNotNull(bidRequest.getWlang(), resultBuilder::addAllWlang);
//        setNotNull(bidRequest.getBcat(), resultBuilder::addAllBcat);
//        setNotNull(bidRequest.getBadv(), resultBuilder::addAllBadv);
//        setNotNull(bidRequest.getBapp(), resultBuilder::addAllBapp);
//        setNotNull(mapNotNull(bidRequest.getSource(), sourceMapper::map), resultBuilder::setSource);
//        setNotNull(mapNotNull(bidRequest.getRegs(), regsMapper::map), resultBuilder::setRegs);
//                .build();
    }

    @Test
    public void testBidRequestMapper() {
    }

    @Test
    public void nativeVideoMapperShouldReturnValidMapper() {
        // given
        final VideoObject videoObject = VideoObject.builder()
                .protocols(singletonList(1))
                .mimes(singletonList("mimes"))
                .minduration(2)
                .maxduration(3)
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> mapper =
                ProtobufRequestUtils.nativeVideoMapper(givenJsonExtensionMapper(OpenRtbTest.video));

        // then
        final OpenRtb.BidRequest.Imp.Video result = mapper.map(videoObject);
        final OpenRtb.BidRequest.Imp.Video expectedResult =
                OpenRtb.BidRequest.Imp.Video.newBuilder()
                        .addAllProtocols(singletonList(1))
                        .addAllMimes(singletonList("mimes"))
                        .setMinduration(2)
                        .setMaxduration(3)
                        .setExtension(OpenRtbTest.video, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void producerMapperShouldReturnValidMapper() {
        // given
        final Producer producer = Producer.builder()
                .id("id")
                .name("name")
                .cat(singletonList("cat"))
                .domain("domain")
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> mapper =
                ProtobufRequestUtils.producerMapper(givenJsonExtensionMapper(OpenRtbTest.producer));

        // then
        final OpenRtb.BidRequest.Producer result = mapper.map(producer);
        final OpenRtb.BidRequest.Producer expectedResult =
                OpenRtb.BidRequest.Producer.newBuilder()
                        .setId("id")
                        .setName("name")
                        .addAllCat(singletonList("cat"))
                        .setDomain("domain")
                        .setExtension(OpenRtbTest.producer, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void pmpMapper() {
    }

    @Test
    public void siteMapper() {
    }

    @Test
    public void geoMapperShouldReturnValidMapper() {
        // given
        final ExtGeo extGeo = ExtGeo.of();
        extGeo.addProperty("field", TextNode.valueOf("fieldValue"));

        final Geo geo = Geo.builder()
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

        // when
        final ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> mapper =
                ProtobufRequestUtils.geoMapper(geoExtMapper);

        // then
        final OpenRtb.BidRequest.Geo result = mapper.map(geo);
        final OpenRtb.BidRequest.Geo expectedResult = OpenRtb.BidRequest.Geo.newBuilder()
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

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void bannerMapperShouldReturnValidMapper() {
        // given
        final Banner banner = Banner.builder()
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

        // when
        final ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> mapper =
                ProtobufRequestUtils.bannerMapper(formatMapper, givenJsonExtensionMapper(OpenRtbTest.banner));

        // then
        final OpenRtb.BidRequest.Imp.Banner result = mapper.map(banner);
        final OpenRtb.BidRequest.Imp.Banner expectedResult =
                OpenRtb.BidRequest.Imp.Banner.newBuilder()
                        .setId("id")
                        .addAllFormat(singletonList(givenProtobufFormat()))
                        .setW(1)
                        .setH(2)
                        .addAllBtype(singletonList(3))
                        .addAllBattr(singletonList(4))
                        .setPos(5)
                        .addAllMimes(singletonList("mimes"))
                        .setTopframe(true)
                        .addAllExpdir(singletonList(7))
                        .addAllApi(singletonList(8))
                        .setVcm(true)
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void nativeImageMapperShouldReturnValidMapper() {
        // when
        final ImageObject imageObject = ImageObject.builder()
                .type(1)
                .w(2)
                .h(3)
                .wmin(4)
                .hmin(5)
                .mimes(singletonList("mimes"))
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> mapper =
                ProtobufRequestUtils.nativeImageMapper(givenJsonExtensionMapper(OpenRtbTest.nativeImage));

        // then
        final OpenRtb.NativeRequest.Asset.Image result = mapper.map(imageObject);
        final OpenRtb.NativeRequest.Asset.Image expectedResult =
                OpenRtb.NativeRequest.Asset.Image.newBuilder()
                        .setType(1)
                        .setW(2)
                        .setH(3)
                        .setWmin(4)
                        .setHmin(5)
                        .addAllMimes(singletonList("mimes"))
                        .setExtension(OpenRtbTest.nativeImage, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void sourceMapper() {
    }

    @Test
    public void contentMapper() {
    }

    @Test
    public void deviceMapper() {
    }

    @Test
    public void nativeDataMapperShouldReturnValidMapper() {
        // given
        final DataObject nativeData = DataObject.builder()
                .type(1)
                .len(2)
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> mapper =
                ProtobufRequestUtils.nativeDataMapper(givenJsonExtensionMapper(OpenRtbTest.nativeData));

        // then
        final OpenRtb.NativeRequest.Asset.Data result = mapper.map(nativeData);
        final OpenRtb.NativeRequest.Asset.Data expectedResult =
                OpenRtb.NativeRequest.Asset.Data.newBuilder()
                        .setType(1)
                        .setLen(2)
                        .setExtension(OpenRtbTest.nativeData, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void dataMapperShouldReturnValidMapper() {
        // given
        final Data data = Data.builder()
                .id("id")
                .name("name")
                .segment(singletonList(givenSegment()))
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<Data, OpenRtb.BidRequest.Data> mapper =
                ProtobufRequestUtils.dataMapper(segmentMapper, givenJsonExtensionMapper(OpenRtbTest.data));

        // then
        final OpenRtb.BidRequest.Data result = mapper.map(data);
        final OpenRtb.BidRequest.Data expectedResult = OpenRtb.BidRequest.Data.newBuilder()
                .setId("id")
                .setName("name")
                .addAllSegment(singletonList(givenProtobufSegment()))
                .setExtension(OpenRtbTest.data, givenProtobufExt("fieldValue"))
                .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void titleMapperShouldReturnValidMapper() {
        // given
        final TitleObject title = TitleObject.builder()
                .len(1)
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> mapper =
                ProtobufRequestUtils.titleMapper(givenJsonExtensionMapper(OpenRtbTest.nativeTitle));

        // then
        final OpenRtb.NativeRequest.Asset.Title result = mapper.map(title);
        final OpenRtb.NativeRequest.Asset.Title expectedResult =
                OpenRtb.NativeRequest.Asset.Title.newBuilder()
                        .setLen(1)
                        .setExtension(OpenRtbTest.nativeTitle, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void metricMapperShouldReturnValidMapper() {
        // given
        final Metric metric = Metric.builder()
                .type("type")
                .value(1.0f)
                .vendor("vendor")
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> mapper =
                ProtobufRequestUtils.metricMapper(givenJsonExtensionMapper(OpenRtbTest.metric));

        // then
        final OpenRtb.BidRequest.Imp.Metric result = mapper.map(metric);
        final OpenRtb.BidRequest.Imp.Metric expectedResult =
                OpenRtb.BidRequest.Imp.Metric.newBuilder()
                        .setType("type")
                        .setValue(1.0)
                        .setVendor("vendor")
                        .setExtension(OpenRtbTest.metric, givenProtobufExt("fieldValue"))
                        .build();

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
        final ExtPublisher extPublisher = ExtPublisher.empty();
        extPublisher.addProperty("field", TextNode.valueOf("fieldValue"));

        final Publisher publisher = Publisher.builder()
                .id("id")
                .domain("domain")
                .cat(singletonList("cat"))
                .name("name")
                .ext(extPublisher)
                .build();

        // when
        final ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> mapper =
                ProtobufRequestUtils.publisherMapper(
                        givenExtensionMapper(
                                ext -> givenProtobufExt(ext.getProperty("field").textValue()),
                                OpenRtbTest.publisher));

        // then
        final OpenRtb.BidRequest.Publisher result = mapper.map(publisher);
        final OpenRtb.BidRequest.Publisher expectedResult = OpenRtb.BidRequest.Publisher.newBuilder()
                .setId("id")
                .setDomain("domain")
                .addAllCat(singletonList("cat"))
                .setName("name")
                .setExtension(OpenRtbTest.publisher, givenProtobufExt("fieldValue"))
                .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void dealMapperShouldReturnValidMapper() {
        // given
        final Deal deal = Deal.builder()
                .id("id")
                .at(1)
                .wseat(singletonList("wseat"))
                .wadomain(singletonList("wadomain"))
                .bidfloor(BigDecimal.ONE)
                .bidfloorcur("bidfloorcur")
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> mapper =
                ProtobufRequestUtils.dealMapper(givenJsonExtensionMapper(OpenRtbTest.deal));

        // then
        final OpenRtb.BidRequest.Imp.Pmp.Deal result = mapper.map(deal);
        final OpenRtb.BidRequest.Imp.Pmp.Deal expectedResult = OpenRtb.BidRequest.Imp.Pmp.Deal.newBuilder()
                .setId("id")
                .setAt(1)
                .addAllWseat(singletonList("wseat"))
                .addAllWadomain(singletonList("wadomain"))
                .setBidfloor(1)
                .setBidfloorcur("bidfloorcur")
                .setExtension(OpenRtbTest.deal, givenProtobufExt("fieldValue"))
                .build();
    }

    @Test
    public void eventTrackerMapperShouldReturnValidMapper() {
        // given
        final EventTracker eventTracker = EventTracker.builder()
                .event(1)
                .methods(List.of(2, 3))
                .ext(givenJsonExt("fieldValue"))
                .build();

        // when
        final ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> mapper =
                ProtobufRequestUtils.eventTrackerMapper(givenJsonExtensionMapper(OpenRtbTest.eventTrackers));

        // then
        final OpenRtb.NativeRequest.EventTrackers result = mapper.map(eventTracker);
        final OpenRtb.NativeRequest.EventTrackers expectedResult =
                OpenRtb.NativeRequest.EventTrackers.newBuilder()
                        .setEvent(1)
                        .addAllMethods(List.of(2, 3))
                        .setExtension(OpenRtbTest.eventTrackers, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void regsMapperShouldReturnValidMapper() {
        // given
        final ExtRegs extRegs = ExtRegs.of(0, "");
        extRegs.addProperty("field", TextNode.valueOf("fieldValue"));

        final Regs regs = Regs.builder()
                .coppa(1)
                .ext(extRegs)
                .build();

        // when
        final ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> mapper =
                ProtobufRequestUtils.regsMapper(
                        givenExtensionMapper(
                                ext -> givenProtobufExt(ext.getProperty("field").textValue()),
                                OpenRtbTest.regs));

        // then
        final OpenRtb.BidRequest.Regs result = mapper.map(regs);
        final OpenRtb.BidRequest.Regs expectedResult =
                OpenRtb.BidRequest.Regs.newBuilder()
                        .setCoppa(true)
                        .setExtension(OpenRtbTest.regs, givenProtobufExt("fieldValue"))
                        .build();

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void appMapper() {
    }

    @Test
    public void audioMapper() {
    }

    @Test
    public void nativeRequestMapper() {
    }

    @Test
    public void testNativeRequestMapper() {
    }

    @Test
    public void nativeMapper() {
    }

    @Test
    public void assetMapper() {
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
    public void impMapper() {
    }

    @Test
    public void videoMapper() {
    }

    @Test
    public void userMapper() {
        final User user = User.builder()
                .id("id")
                .buyeruid("buyeruid")
                .yob(1)
                .gender("gender")
                .keywords("keywords")
                .customdata("customdata").build();
//        setNotNull(user.getCustomdata(), resultBuilder::setCustomdata);
//        setNotNull(mapNotNull(user.getGeo(), geoMapper::map), resultBuilder::setGeo);
//        setNotNull(mapList(user.getData(), dataMapper::map), resultBuilder::addAllData);
//                .build();
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

    private static <ContainingType extends Message>
    JsonProtobufExtensionMapper<ContainingType, OpenRtbTest.TestExt> givenJsonExtensionMapper(
            Extension<ContainingType, OpenRtbTest.TestExt> extensionDescriptor) {

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
