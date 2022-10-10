package org.prebid.server.protobuf.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ExtensionLite;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProtobufRequestUtils {

    private ProtobufRequestUtils() {
    }

    public static ProtobufMapper<BidRequest, OpenRtb.BidRequest> bidRequestMapper(
            RequestExtensionMappersSpecification spec) {

        final ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper =
                bannerMapper(formatMapper(spec.formatExtMapper()), spec.bannerExtMapper());

        final ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> assetMapper = assetMapper(
                titleMapper(spec.nativeTitleExtMapper()),
                nativeImageMapper(spec.nativeImageExtMapper()),
                nativeVideoMapper(spec.nativeVideoExtMapper()),
                nativeDataMapper(spec.nativeDataExtMapper()),
                spec.nativeAssetExtMapper());

        final ProtobufMapper<String, OpenRtb.NativeRequest> nativeRequestMapper = nativeRequestMapper(
                spec.objectMapper(),
                nativeRequestMapper(
                        assetMapper,
                        eventTrackerMapper(spec.nativeEventTrackerExtMapper()),
                        spec.nativeRequestExtMapper()));

        final ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> impMapper = impMapper(
                metricMapper(spec.metricExtMapper()),
                bannerMapper,
                videoMapper(bannerMapper, spec.videoExtMapper()),
                audioMapper(bannerMapper, spec.audioExtMapper()),
                nativeMapper(nativeRequestMapper, spec.nativeExtMapper()),
                pmpMapper(dealMapper(spec.dealExtMapper()), spec.pmpExtMapper()),
                spec.impExtMapper());

        final ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper =
                dataMapper(segmentMapper(spec.segmentExtMapper()), spec.dataExtMapper());

        final ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper =
                publisherMapper(spec.publisherExtMapper());

        final ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper =
                contentMapper(producerMapper(spec.producerExtMapper()), dataMapper, spec.contentExtMapper());

        final ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper = geoMapper(spec.geoExtMapper());

        return bidRequestMapper(
                impMapper,
                siteMapper(publisherMapper, contentMapper, spec.siteExtMapper()),
                appMapper(publisherMapper, contentMapper, spec.appExtMapper()),
                deviceMapper(geoMapper, spec.deviceExtMapper()),
                userMapper(geoMapper, dataMapper, spec.userExtMapper()),
                sourceMapper(spec.sourceExtMapper()),
                regsMapper(spec.regsExtMapper()),
                spec.bidRequestExtMapper());
    }

    public static <ProtobufExtensionType> ProtobufMapper<BidRequest, OpenRtb.BidRequest> bidRequestMapper(
            ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> impMapper,
            ProtobufMapper<Site, OpenRtb.BidRequest.Site> siteMapper,
            ProtobufMapper<App, OpenRtb.BidRequest.App> appMapper,
            ProtobufMapper<Device, OpenRtb.BidRequest.Device> deviceMapper,
            ProtobufMapper<User, OpenRtb.BidRequest.User> userMapper,
            ProtobufMapper<Source, OpenRtb.BidRequest.Source> sourceMapper,
            ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> regsMapper,
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest, ExtRequest, ProtobufExtensionType> extMapper) {

        return (BidRequest bidRequest) -> {
            final OpenRtb.BidRequest.Builder resultBuilder = OpenRtb.BidRequest.newBuilder();

            setNotNull(bidRequest.getId(), resultBuilder::setId);
            setNotNull(mapList(bidRequest.getImp(), impMapper::map), resultBuilder::addAllImp);
            setNotNull(mapNotNull(bidRequest.getSite(), siteMapper::map), resultBuilder::setSite);
            setNotNull(mapNotNull(bidRequest.getApp(), appMapper::map), resultBuilder::setApp);
            setNotNull(mapNotNull(bidRequest.getDevice(), deviceMapper::map), resultBuilder::setDevice);
            setNotNull(mapNotNull(bidRequest.getUser(), userMapper::map), resultBuilder::setUser);
            setNotNull(mapNotNull(bidRequest.getTest(), BooleanUtils::toBoolean), resultBuilder::setTest);
            setNotNull(bidRequest.getAt(), resultBuilder::setAt);
            setNotNull(mapNotNull(bidRequest.getTmax(), Long::intValue), resultBuilder::setTmax);
            setNotNull(bidRequest.getWseat(), resultBuilder::addAllWseat);
            setNotNull(bidRequest.getBseat(), resultBuilder::addAllBseat);
            setNotNull(mapNotNull(bidRequest.getAllimps(), BooleanUtils::toBoolean), resultBuilder::setAllimps);
            setNotNull(bidRequest.getCur(), resultBuilder::addAllCur);
            setNotNull(bidRequest.getWlang(), resultBuilder::addAllWlang);
            setNotNull(bidRequest.getBcat(), resultBuilder::addAllBcat);
            setNotNull(bidRequest.getBadv(), resultBuilder::addAllBadv);
            setNotNull(bidRequest.getBapp(), resultBuilder::addAllBapp);
            setNotNull(mapNotNull(bidRequest.getSource(), sourceMapper::map), resultBuilder::setSource);
            setNotNull(mapNotNull(bidRequest.getRegs(), regsMapper::map), resultBuilder::setRegs);

            mapAndSetExtension(extMapper, bidRequest.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> nativeVideoMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ProtobufExtensionType> extMapper) {

        return (VideoObject video) -> {
            final OpenRtb.BidRequest.Imp.Video.Builder resultBuilder = OpenRtb.BidRequest.Imp.Video.newBuilder();

            setNotNull(video.getMimes(), resultBuilder::addAllMimes);
            setNotNull(video.getMinduration(), resultBuilder::setMinduration);
            setNotNull(video.getMaxduration(), resultBuilder::setMaxduration);
            setNotNull(video.getProtocols(), resultBuilder::addAllProtocols);

            mapAndSetExtension(extMapper, video.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> producerMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Producer, ProtobufExtensionType> extMapper) {

        return (Producer producer) -> {
            final OpenRtb.BidRequest.Producer.Builder resultBuilder = OpenRtb.BidRequest.Producer.newBuilder();

            setNotNull(producer.getId(), resultBuilder::setId);
            setNotNull(producer.getName(), resultBuilder::setName);
            setNotNull(producer.getCat(), resultBuilder::addAllCat);
            setNotNull(producer.getDomain(), resultBuilder::setDomain);

            mapAndSetExtension(extMapper, producer.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> pmpMapper(
            ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> dealsMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp, ProtobufExtensionType> extMapper) {

        return (Pmp pmp) -> {
            final OpenRtb.BidRequest.Imp.Pmp.Builder resultBuilder = OpenRtb.BidRequest.Imp.Pmp.newBuilder();

            setNotNull(mapNotNull(pmp.getPrivateAuction(), BooleanUtils::toBoolean), resultBuilder::setPrivateAuction);
            setNotNull(mapList(pmp.getDeals(), dealsMapper::map), resultBuilder::addAllDeals);

            mapAndSetExtension(extMapper, pmp.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Site, OpenRtb.BidRequest.Site> siteMapper(
            ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper,
            ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper,
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Site, ExtSite, ProtobufExtensionType> extMapper) {

        return (Site site) -> {
            final OpenRtb.BidRequest.Site.Builder resultBuilder = OpenRtb.BidRequest.Site.newBuilder();

            setNotNull(site.getId(), resultBuilder::setId);
            setNotNull(site.getName(), resultBuilder::setName);
            setNotNull(site.getDomain(), resultBuilder::setDomain);
            setNotNull(site.getCat(), resultBuilder::addAllCat);
            setNotNull(site.getSectioncat(), resultBuilder::addAllSectioncat);
            setNotNull(site.getPagecat(), resultBuilder::addAllPagecat);
            setNotNull(site.getPage(), resultBuilder::setPage);
            setNotNull(site.getRef(), resultBuilder::setRef);
            setNotNull(site.getSearch(), resultBuilder::setSearch);
            setNotNull(mapNotNull(site.getMobile(), BooleanUtils::toBoolean), resultBuilder::setMobile);
            setNotNull(mapNotNull(site.getPrivacypolicy(), BooleanUtils::toBoolean), resultBuilder::setPrivacypolicy);
            setNotNull(mapNotNull(site.getPublisher(), publisherMapper::map), resultBuilder::setPublisher);
            setNotNull(mapNotNull(site.getContent(), contentMapper::map), resultBuilder::setContent);
            setNotNull(site.getKeywords(), resultBuilder::setKeywords);

            mapAndSetExtension(extMapper, site.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper(
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Geo, ExtGeo, ProtobufExtensionType> extMapper) {

        return (Geo geo) -> {
            final OpenRtb.BidRequest.Geo.Builder resultBuilder = OpenRtb.BidRequest.Geo.newBuilder();

            setNotNull(mapNotNull(geo.getLat(), Float::doubleValue), resultBuilder::setLat);
            setNotNull(mapNotNull(geo.getLon(), Float::doubleValue), resultBuilder::setLon);
            setNotNull(geo.getType(), resultBuilder::setType);
            setNotNull(geo.getAccuracy(), resultBuilder::setAccuracy);
            setNotNull(geo.getLastfix(), resultBuilder::setLastfix);
            setNotNull(geo.getIpservice(), resultBuilder::setIpservice);
            setNotNull(geo.getCountry(), resultBuilder::setCountry);
            setNotNull(geo.getRegion(), resultBuilder::setRegion);
            setNotNull(geo.getRegionfips104(), resultBuilder::setRegionfips104);
            setNotNull(geo.getMetro(), resultBuilder::setMetro);
            setNotNull(geo.getCity(), resultBuilder::setCity);
            setNotNull(geo.getZip(), resultBuilder::setZip);
            setNotNull(geo.getUtcoffset(), resultBuilder::setUtcoffset);

            mapAndSetExtension(extMapper, geo.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper(
            ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> formatMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner, ProtobufExtensionType> extMapper) {

        return (Banner banner) -> {
            final OpenRtb.BidRequest.Imp.Banner.Builder resultBuilder = OpenRtb.BidRequest.Imp.Banner.newBuilder();

            setNotNull(mapList(banner.getFormat(), formatMapper::map), resultBuilder::addAllFormat);
            setNotNull(banner.getW(), resultBuilder::setW);
            setNotNull(banner.getH(), resultBuilder::setH);
            setNotNull(banner.getBtype(), resultBuilder::addAllBtype);
            setNotNull(banner.getBattr(), resultBuilder::addAllBattr);
            setNotNull(banner.getPos(), resultBuilder::setPos);
            setNotNull(banner.getMimes(), resultBuilder::addAllMimes);
            setNotNull(mapNotNull(banner.getTopframe(), BooleanUtils::toBoolean), resultBuilder::setTopframe);
            setNotNull(banner.getExpdir(), resultBuilder::addAllExpdir);
            setNotNull(banner.getApi(), resultBuilder::addAllApi);
            setNotNull(banner.getId(), resultBuilder::setId);
            setNotNull(mapNotNull(banner.getVcm(), BooleanUtils::toBoolean), resultBuilder::setVcm);

            mapAndSetExtension(extMapper, banner.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType>
            ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> nativeImageMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Image, ProtobufExtensionType> extMapper) {

        return (ImageObject imageObject) -> {
            final OpenRtb.NativeRequest.Asset.Image.Builder resultBuilder =
                    OpenRtb.NativeRequest.Asset.Image.newBuilder();

            setNotNull(imageObject.getType(), resultBuilder::setType);
            setNotNull(imageObject.getW(), resultBuilder::setW);
            setNotNull(imageObject.getWmin(), resultBuilder::setWmin);
            setNotNull(imageObject.getH(), resultBuilder::setH);
            setNotNull(imageObject.getHmin(), resultBuilder::setHmin);
            setNotNull(imageObject.getMimes(), resultBuilder::addAllMimes);

            mapAndSetExtension(extMapper, imageObject.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Source, OpenRtb.BidRequest.Source> sourceMapper(
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Source, ExtSource, ProtobufExtensionType> extMapper) {

        return (Source source) -> {
            final OpenRtb.BidRequest.Source.Builder resultBuilder = OpenRtb.BidRequest.Source.newBuilder();

            setNotNull(mapNotNull(source.getFd(), BooleanUtils::toBoolean), resultBuilder::setFd);
            setNotNull(source.getTid(), resultBuilder::setTid);
            setNotNull(source.getPchain(), resultBuilder::setPchain);

            mapAndSetExtension(extMapper, source.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper(
            ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> producerMapper,
            ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Content, ProtobufExtensionType> extMapper) {

        return (Content content) -> {
            final OpenRtb.BidRequest.Content.Builder resultBuilder = OpenRtb.BidRequest.Content.newBuilder();

            setNotNull(content.getId(), resultBuilder::setId);
            setNotNull(content.getEpisode(), resultBuilder::setEpisode);
            setNotNull(content.getTitle(), resultBuilder::setTitle);
            setNotNull(content.getSeries(), resultBuilder::setSeries);
            setNotNull(content.getSeason(), resultBuilder::setSeason);
            setNotNull(content.getArtist(), resultBuilder::setArtist);
            setNotNull(content.getGenre(), resultBuilder::setGenre);
            setNotNull(content.getAlbum(), resultBuilder::setAlbum);
            setNotNull(content.getIsrc(), resultBuilder::setIsrc);
            setNotNull(mapNotNull(content.getProducer(), producerMapper::map), resultBuilder::setProducer);
            setNotNull(content.getUrl(), resultBuilder::setUrl);
            setNotNull(content.getCat(), resultBuilder::addAllCat);
            setNotNull(content.getProdq(), resultBuilder::setProdq);
            setNotNull(content.getContext(), resultBuilder::setContext);
            setNotNull(content.getContentrating(), resultBuilder::setContentrating);
            setNotNull(content.getUserrating(), resultBuilder::setUserrating);
            setNotNull(content.getQagmediarating(), resultBuilder::setQagmediarating);
            setNotNull(content.getKeywords(), resultBuilder::setKeywords);
            setNotNull(mapNotNull(content.getLivestream(), BooleanUtils::toBoolean), resultBuilder::setLivestream);
            setNotNull(
                    mapNotNull(content.getSourcerelationship(), BooleanUtils::toBoolean),
                    resultBuilder::setSourcerelationship);
            setNotNull(content.getLen(), resultBuilder::setLen);
            setNotNull(content.getLanguage(), resultBuilder::setLanguage);
            setNotNull(mapNotNull(content.getEmbeddable(), BooleanUtils::toBoolean), resultBuilder::setEmbeddable);
            setNotNull(mapList(content.getData(), dataMapper::map), resultBuilder::addAllData);

            mapAndSetExtension(extMapper, content.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Device, OpenRtb.BidRequest.Device> deviceMapper(
            ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper,
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Device, ExtDevice, ProtobufExtensionType> extMapper) {

        return (Device device) -> {
            final OpenRtb.BidRequest.Device.Builder resultBuilder = OpenRtb.BidRequest.Device.newBuilder();

            setNotNull(mapNotNull(device.getGeo(), geoMapper::map), resultBuilder::setGeo);
            setNotNull(mapNotNull(device.getDnt(), BooleanUtils::toBoolean), resultBuilder::setDnt);
            setNotNull(mapNotNull(device.getLmt(), BooleanUtils::toBoolean), resultBuilder::setLmt);
            setNotNull(device.getUa(), resultBuilder::setUa);
            setNotNull(device.getIp(), resultBuilder::setIp);
            setNotNull(device.getIpv6(), resultBuilder::setIpv6);
            setNotNull(device.getDevicetype(), resultBuilder::setDevicetype);
            setNotNull(device.getMake(), resultBuilder::setMake);
            setNotNull(device.getModel(), resultBuilder::setModel);
            setNotNull(device.getOs(), resultBuilder::setOs);
            setNotNull(device.getOsv(), resultBuilder::setOsv);
            setNotNull(device.getHwv(), resultBuilder::setHwv);
            setNotNull(device.getH(), resultBuilder::setH);
            setNotNull(device.getW(), resultBuilder::setW);
            setNotNull(device.getPpi(), resultBuilder::setPpi);
            setNotNull(mapNotNull(device.getPxratio(), BigDecimal::doubleValue), resultBuilder::setPxratio);
            setNotNull(mapNotNull(device.getJs(), BooleanUtils::toBoolean), resultBuilder::setJs);
            setNotNull(mapNotNull(device.getGeofetch(), BooleanUtils::toBoolean), resultBuilder::setGeofetch);
            setNotNull(device.getFlashver(), resultBuilder::setFlashver);
            setNotNull(device.getLanguage(), resultBuilder::setLanguage);
            setNotNull(device.getCarrier(), resultBuilder::setCarrier);
            setNotNull(device.getMccmnc(), resultBuilder::setMccmnc);
            setNotNull(device.getConnectiontype(), resultBuilder::setConnectiontype);
            setNotNull(device.getIfa(), resultBuilder::setIfa);
            setNotNull(device.getDidsha1(), resultBuilder::setDidsha1);
            setNotNull(device.getDidmd5(), resultBuilder::setDidmd5);
            setNotNull(device.getDpidsha1(), resultBuilder::setDpidsha1);
            setNotNull(device.getDpidmd5(), resultBuilder::setDpidmd5);
            setNotNull(device.getMacsha1(), resultBuilder::setMacsha1);
            setNotNull(device.getMacmd5(), resultBuilder::setMacmd5);

            mapAndSetExtension(extMapper, device.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> nativeDataMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Data, ProtobufExtensionType> extMapper) {

        return (DataObject data) -> {
            final OpenRtb.NativeRequest.Asset.Data.Builder resultBuilder =
                    OpenRtb.NativeRequest.Asset.Data.newBuilder();

            setNotNull(data.getType(), resultBuilder::setType);
            setNotNull(data.getLen(), resultBuilder::setLen);

            mapAndSetExtension(extMapper, data.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper(
            ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> segmentMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data, ProtobufExtensionType> extMapper) {

        return (Data data) -> {
            final OpenRtb.BidRequest.Data.Builder resultBuilder = OpenRtb.BidRequest.Data.newBuilder();

            setNotNull(data.getId(), resultBuilder::setId);
            setNotNull(data.getName(), resultBuilder::setName);
            setNotNull(mapList(data.getSegment(), segmentMapper::map), resultBuilder::addAllSegment);

            mapAndSetExtension(extMapper, data.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> titleMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Title, ProtobufExtensionType> extMapper) {

        return (TitleObject titleObject) -> {
            final OpenRtb.NativeRequest.Asset.Title.Builder resultBuilder =
                    OpenRtb.NativeRequest.Asset.Title.newBuilder();

            setNotNull(titleObject.getLen(), resultBuilder::setLen);

            mapAndSetExtension(extMapper, titleObject.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> metricMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Metric, ProtobufExtensionType> extMapper) {

        return (Metric metric) -> {
            final OpenRtb.BidRequest.Imp.Metric.Builder resultBuilder = OpenRtb.BidRequest.Imp.Metric.newBuilder();

            setNotNull(metric.getType(), resultBuilder::setType);
            setNotNull(mapNotNull(metric.getValue(), Float::doubleValue), resultBuilder::setValue);
            setNotNull(metric.getVendor(), resultBuilder::setVendor);

            mapAndSetExtension(extMapper, metric.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Format, OpenRtb.BidRequest.Imp.Banner.Format> formatMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner.Format, ProtobufExtensionType> extMapper) {

        return (Format format) -> {
            final OpenRtb.BidRequest.Imp.Banner.Format.Builder resultBuilder =
                    OpenRtb.BidRequest.Imp.Banner.Format.newBuilder();

            setNotNull(format.getW(), resultBuilder::setW);
            setNotNull(format.getH(), resultBuilder::setH);
            setNotNull(format.getWratio(), resultBuilder::setWratio);
            setNotNull(format.getHratio(), resultBuilder::setHratio);
            setNotNull(format.getWmin(), resultBuilder::setWmin);

            mapAndSetExtension(extMapper, format.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper(
            ProtobufForwardExtensionMapper<
                    OpenRtb.BidRequest.Publisher,
                    ExtPublisher,
                    ProtobufExtensionType
                    > extMapper) {

        return (Publisher publisher) -> {
            final OpenRtb.BidRequest.Publisher.Builder resultBuilder = OpenRtb.BidRequest.Publisher.newBuilder();

            setNotNull(publisher.getId(), resultBuilder::setId);
            setNotNull(publisher.getName(), resultBuilder::setName);
            setNotNull(publisher.getCat(), resultBuilder::addAllCat);
            setNotNull(publisher.getDomain(), resultBuilder::setDomain);

            mapAndSetExtension(extMapper, publisher.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Deal, OpenRtb.BidRequest.Imp.Pmp.Deal> dealMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp.Deal, ProtobufExtensionType> extMapper) {

        return (Deal deal) -> {
            final OpenRtb.BidRequest.Imp.Pmp.Deal.Builder resultBuilder = OpenRtb.BidRequest.Imp.Pmp.Deal.newBuilder();

            setNotNull(deal.getId(), resultBuilder::setId);
            setNotNull(mapNotNull(deal.getBidfloor(), BigDecimal::doubleValue), resultBuilder::setBidfloor);
            setNotNull(deal.getBidfloorcur(), resultBuilder::setBidfloorcur);
            setNotNull(deal.getAt(), resultBuilder::setAt);
            setNotNull(deal.getWseat(), resultBuilder::addAllWseat);
            setNotNull(deal.getWadomain(), resultBuilder::addAllWadomain);

            mapAndSetExtension(extMapper, deal.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType>
            ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> eventTrackerMapper(
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.EventTrackers, ProtobufExtensionType> extMapper) {

        return (EventTracker eventTracker) -> {
            final OpenRtb.NativeRequest.EventTrackers.Builder resultBuilder =
                    OpenRtb.NativeRequest.EventTrackers.newBuilder();

            setNotNull(eventTracker.getEvent(), resultBuilder::setEvent);
            setNotNull(eventTracker.getMethods(), resultBuilder::addAllMethods);

            mapAndSetExtension(extMapper, eventTracker.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> regsMapper(
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Regs, ExtRegs, ProtobufExtensionType> extMapper) {

        return (Regs regs) -> {
            final OpenRtb.BidRequest.Regs.Builder resultBuilder = OpenRtb.BidRequest.Regs.newBuilder();

            setNotNull(mapNotNull(regs.getCoppa(), BooleanUtils::toBoolean), resultBuilder::setCoppa);

            mapAndSetExtension(extMapper, regs.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<App, OpenRtb.BidRequest.App> appMapper(
            ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper,
            ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper,
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.App, ExtApp, ProtobufExtensionType> extMapper) {

        return (App app) -> {
            final OpenRtb.BidRequest.App.Builder resultBuilder = OpenRtb.BidRequest.App.newBuilder();

            setNotNull(app.getId(), resultBuilder::setId);
            setNotNull(app.getName(), resultBuilder::setName);
            setNotNull(app.getBundle(), resultBuilder::setBundle);
            setNotNull(app.getDomain(), resultBuilder::setDomain);
            setNotNull(app.getStoreurl(), resultBuilder::setStoreurl);
            setNotNull(app.getCat(), resultBuilder::addAllCat);
            setNotNull(app.getSectioncat(), resultBuilder::addAllSectioncat);
            setNotNull(app.getPagecat(), resultBuilder::addAllPagecat);
            setNotNull(app.getVer(), resultBuilder::setVer);
            setNotNull(mapNotNull(app.getPrivacypolicy(), BooleanUtils::toBoolean), resultBuilder::setPrivacypolicy);
            setNotNull(mapNotNull(app.getPaid(), BooleanUtils::toBoolean), resultBuilder::setPaid);
            setNotNull(mapNotNull(app.getPublisher(), publisherMapper::map), resultBuilder::setPublisher);
            setNotNull(mapNotNull(app.getContent(), contentMapper::map), resultBuilder::setContent);
            setNotNull(app.getKeywords(), resultBuilder::setKeywords);

            mapAndSetExtension(extMapper, app.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> audioMapper(
            ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Audio, ProtobufExtensionType> extMapper) {

        return (Audio audio) -> {
            final OpenRtb.BidRequest.Imp.Audio.Builder resultBuilder = OpenRtb.BidRequest.Imp.Audio.newBuilder();

            setNotNull(audio.getMimes(), resultBuilder::addAllMimes);
            setNotNull(audio.getMinduration(), resultBuilder::setMinduration);
            setNotNull(audio.getMaxduration(), resultBuilder::setMaxduration);
            setNotNull(audio.getProtocols(), resultBuilder::addAllProtocols);
            setNotNull(audio.getStartdelay(), resultBuilder::setStartdelay);
            setNotNull(audio.getSequence(), resultBuilder::setSequence);
            setNotNull(audio.getBattr(), resultBuilder::addAllBattr);
            setNotNull(audio.getMaxextended(), resultBuilder::setMaxextended);
            setNotNull(audio.getMinbitrate(), resultBuilder::setMinbitrate);
            setNotNull(audio.getMaxbitrate(), resultBuilder::setMaxbitrate);
            setNotNull(audio.getDelivery(), resultBuilder::addAllDelivery);
            setNotNull(mapList(audio.getCompanionad(), bannerMapper::map), resultBuilder::addAllCompanionad);
            setNotNull(audio.getApi(), resultBuilder::addAllApi);
            setNotNull(audio.getCompaniontype(), resultBuilder::addAllCompaniontype);
            setNotNull(audio.getMaxseq(), resultBuilder::setMaxseq);
            setNotNull(audio.getFeed(), resultBuilder::setFeed);
            setNotNull(mapNotNull(audio.getStitched(), BooleanUtils::toBoolean), resultBuilder::setStitched);
            setNotNull(audio.getNvol(), resultBuilder::setNvol);

            mapAndSetExtension(extMapper, audio.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static ProtobufMapper<String, OpenRtb.NativeRequest> nativeRequestMapper(
            ObjectMapper objectMapper,
            ProtobufMapper<Request, OpenRtb.NativeRequest> nativeRequestMapper) {

        return (String value) -> {
            try {
                final Request request = objectMapper.readValue(value, Request.class);
                return nativeRequestMapper.map(request);
            } catch (Throwable e) {
                return null;
            }
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Request, OpenRtb.NativeRequest> nativeRequestMapper(
            ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> assetsMapper,
            ProtobufMapper<EventTracker, OpenRtb.NativeRequest.EventTrackers> eventTrackerMapper,
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest, ProtobufExtensionType> extMapper) {

        return (Request request) -> {
            final OpenRtb.NativeRequest.Builder resultBuilder = OpenRtb.NativeRequest.newBuilder();

            setNotNull(request.getVer(), resultBuilder::setVer);
            setNotNull(request.getContext(), resultBuilder::setContext);
            setNotNull(request.getContextsubtype(), resultBuilder::setContextsubtype);
            setNotNull(request.getPlcmttype(), resultBuilder::setPlcmttype);
            setNotNull(request.getPlcmtcnt(), resultBuilder::setPlcmtcnt);
            setNotNull(request.getSeq(), resultBuilder::setSeq);
            setNotNull(mapList(request.getAssets(), assetsMapper::map), resultBuilder::addAllAssets);
            setNotNull(mapNotNull(request.getAurlsupport(), BooleanUtils::toBoolean), resultBuilder::setAurlsupport);
            setNotNull(mapNotNull(request.getDurlsupport(), BooleanUtils::toBoolean), resultBuilder::setDurlsupport);
            setNotNull(
                    mapList(request.getEventtrackers(), eventTrackerMapper::map),
                    resultBuilder::addAllEventtrackers);
            setNotNull(mapNotNull(request.getPrivacy(), BooleanUtils::toBoolean), resultBuilder::setPrivacy);

            mapAndSetExtension(extMapper, request.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> nativeMapper(
            ProtobufMapper<String, OpenRtb.NativeRequest> nativeRequestMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Native, ProtobufExtensionType> extMapper) {

        return (Native xNative) -> {
            final OpenRtb.BidRequest.Imp.Native.Builder resultBuilder = OpenRtb.BidRequest.Imp.Native.newBuilder();

            final OpenRtb.NativeRequest nativeRequest = nativeRequestMapper.map(xNative.getRequest());
            if (nativeRequest != null) {
                resultBuilder.setRequestNative(nativeRequest);
            } else {
                resultBuilder.setRequest(xNative.getRequest());
            }

            setNotNull(xNative.getVer(), resultBuilder::setVer);
            setNotNull(xNative.getApi(), resultBuilder::addAllApi);
            setNotNull(xNative.getBattr(), resultBuilder::addAllBattr);

            mapAndSetExtension(extMapper, xNative.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Asset, OpenRtb.NativeRequest.Asset> assetMapper(
            ProtobufMapper<TitleObject, OpenRtb.NativeRequest.Asset.Title> titleMapper,
            ProtobufMapper<ImageObject, OpenRtb.NativeRequest.Asset.Image> imgMapper,
            ProtobufMapper<VideoObject, OpenRtb.BidRequest.Imp.Video> videoMapper,
            ProtobufMapper<DataObject, OpenRtb.NativeRequest.Asset.Data> dataMapper,
            JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset, ProtobufExtensionType> extMapper) {

        return (Asset asset) -> {
            final OpenRtb.NativeRequest.Asset.Builder resultBuilder = OpenRtb.NativeRequest.Asset.newBuilder();

            setNotNull(asset.getId(), resultBuilder::setId);
            setNotNull(mapNotNull(asset.getRequired(), BooleanUtils::toBoolean), resultBuilder::setRequired);
            setNotNull(mapNotNull(asset.getTitle(), titleMapper::map), resultBuilder::setTitle);
            setNotNull(mapNotNull(asset.getImg(), imgMapper::map), resultBuilder::setImg);
            setNotNull(mapNotNull(asset.getVideo(), videoMapper::map), resultBuilder::setVideo);
            setNotNull(mapNotNull(asset.getData(), dataMapper::map), resultBuilder::setData);

            mapAndSetExtension(extMapper, asset.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Segment, OpenRtb.BidRequest.Data.Segment> segmentMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data.Segment, ProtobufExtensionType> extMapper) {

        return (Segment segment) -> {
            final OpenRtb.BidRequest.Data.Segment.Builder resultBuilder = OpenRtb.BidRequest.Data.Segment.newBuilder();

            setNotNull(segment.getId(), resultBuilder::setId);
            setNotNull(segment.getName(), resultBuilder::setName);
            setNotNull(segment.getValue(), resultBuilder::setValue);

            mapAndSetExtension(extMapper, segment.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Imp, OpenRtb.BidRequest.Imp> impMapper(
            ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> metricMapper,
            ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper,
            ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> videoMapper,
            ProtobufMapper<Audio, OpenRtb.BidRequest.Imp.Audio> audioMapper,
            ProtobufMapper<Native, OpenRtb.BidRequest.Imp.Native> nativeMapper,
            ProtobufMapper<Pmp, OpenRtb.BidRequest.Imp.Pmp> pmpMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp, ProtobufExtensionType> extMapper) {

        return (Imp imp) -> {
            final OpenRtb.BidRequest.Imp.Builder resultBuilder = OpenRtb.BidRequest.Imp.newBuilder();

            setNotNull(imp.getId(), resultBuilder::setId);
            setNotNull(mapList(imp.getMetric(), metricMapper::map), resultBuilder::addAllMetric);
            setNotNull(mapNotNull(imp.getBanner(), bannerMapper::map), resultBuilder::setBanner);
            setNotNull(mapNotNull(imp.getVideo(), videoMapper::map), resultBuilder::setVideo);
            setNotNull(mapNotNull(imp.getAudio(), audioMapper::map), resultBuilder::setAudio);
            setNotNull(mapNotNull(imp.getXNative(), nativeMapper::map), resultBuilder::setNative);
            setNotNull(mapNotNull(imp.getPmp(), pmpMapper::map), resultBuilder::setPmp);
            setNotNull(imp.getDisplaymanager(), resultBuilder::setDisplaymanager);
            setNotNull(imp.getDisplaymanagerver(), resultBuilder::setDisplaymanagerver);
            setNotNull(mapNotNull(imp.getInstl(), BooleanUtils::toBoolean), resultBuilder::setInstl);
            setNotNull(imp.getTagid(), resultBuilder::setTagid);
            setNotNull(mapNotNull(imp.getBidfloor(), BigDecimal::doubleValue), resultBuilder::setBidfloor);
            setNotNull(imp.getBidfloorcur(), resultBuilder::setBidfloorcur);
            setNotNull(mapNotNull(imp.getClickbrowser(), BooleanUtils::toBoolean), resultBuilder::setClickbrowser);
            setNotNull(mapNotNull(imp.getSecure(), BooleanUtils::toBoolean), resultBuilder::setSecure);
            setNotNull(imp.getIframebuster(), resultBuilder::addAllIframebuster);
            setNotNull(imp.getExp(), resultBuilder::setExp);

            mapAndSetExtension(extMapper, imp.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<Video, OpenRtb.BidRequest.Imp.Video> videoMapper(
            ProtobufMapper<Banner, OpenRtb.BidRequest.Imp.Banner> bannerMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ProtobufExtensionType> extMapper) {

        return (Video video) -> {
            final OpenRtb.BidRequest.Imp.Video.Builder resultBuilder = OpenRtb.BidRequest.Imp.Video.newBuilder();

            setNotNull(video.getMimes(), resultBuilder::addAllMimes);
            setNotNull(video.getMinduration(), resultBuilder::setMinduration);
            setNotNull(video.getMaxduration(), resultBuilder::setMaxduration);
            setNotNull(video.getStartdelay(), resultBuilder::setStartdelay);
            setNotNull(video.getProtocols(), resultBuilder::addAllProtocols);
            setNotNull(video.getW(), resultBuilder::setW);
            setNotNull(video.getH(), resultBuilder::setH);
            setNotNull(video.getPlacement(), resultBuilder::setPlacement);
            setNotNull(video.getLinearity(), resultBuilder::setLinearity);
            setNotNull(mapNotNull(video.getSkip(), BooleanUtils::toBoolean), resultBuilder::setSkip);
            setNotNull(video.getSkipmin(), resultBuilder::setSkipmin);
            setNotNull(video.getSkipafter(), resultBuilder::setSkipafter);
            setNotNull(video.getSequence(), resultBuilder::setSequence);
            setNotNull(video.getBattr(), resultBuilder::addAllBattr);
            setNotNull(video.getMaxextended(), resultBuilder::setMaxextended);
            setNotNull(video.getMinbitrate(), resultBuilder::setMinbitrate);
            setNotNull(video.getMaxbitrate(), resultBuilder::setMaxbitrate);
            setNotNull(mapNotNull(video.getBoxingallowed(), BooleanUtils::toBoolean), resultBuilder::setBoxingallowed);
            setNotNull(video.getPlaybackmethod(), resultBuilder::addAllPlaybackmethod);
            setNotNull(video.getPlaybackend(), resultBuilder::setPlaybackend);
            setNotNull(video.getDelivery(), resultBuilder::addAllDelivery);
            setNotNull(video.getPos(), resultBuilder::setPos);
            setNotNull(mapList(video.getCompanionad(), bannerMapper::map), resultBuilder::addAllCompanionad);
            setNotNull(video.getApi(), resultBuilder::addAllApi);
            setNotNull(video.getCompaniontype(), resultBuilder::addAllCompaniontype);

            mapAndSetExtension(extMapper, video.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<User, OpenRtb.BidRequest.User> userMapper(
            ProtobufMapper<Geo, OpenRtb.BidRequest.Geo> geoMapper,
            ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper,
            ProtobufForwardExtensionMapper<OpenRtb.BidRequest.User, ExtUser, ProtobufExtensionType> extMapper) {

        return (User user) -> {
            final OpenRtb.BidRequest.User.Builder resultBuilder = OpenRtb.BidRequest.User.newBuilder();

            setNotNull(user.getId(), resultBuilder::setId);
            setNotNull(user.getBuyeruid(), resultBuilder::setBuyeruid);
            setNotNull(user.getYob(), resultBuilder::setYob);
            setNotNull(user.getGender(), resultBuilder::setGender);
            setNotNull(user.getKeywords(), resultBuilder::setKeywords);
            setNotNull(user.getCustomdata(), resultBuilder::setCustomdata);
            setNotNull(mapNotNull(user.getGeo(), geoMapper::map), resultBuilder::setGeo);
            setNotNull(mapList(user.getData(), dataMapper::map), resultBuilder::addAllData);

            mapAndSetExtension(extMapper, user.getExt(), resultBuilder::setExtension);

            return resultBuilder.build();
        };
    }

    private static <T, U> U mapNotNull(T value, Function<T, U> mapper) {
        return value != null ? mapper.apply(value) : null;
    }

    private static <T> void setNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static <T, U> List<U> mapList(List<T> values, Function<T, U> mapper) {
        return CollectionUtils.isEmpty(values)
                ? Collections.emptyList()
                : values.stream().map(mapper).toList();
    }

    private static <ContainingType extends Message, FromType, ToType> void mapAndSetExtension(
            ProtobufForwardExtensionMapper<ContainingType, FromType, ToType> mapper,
            FromType value,
            BiConsumer<ExtensionLite<ContainingType, ToType>, ToType> extensionSetter) {

        if (mapper == null || value == null) {
            return;
        }

        final ToType mappedExt = mapper.map(value);
        if (mappedExt != null) {
            extensionSetter.accept(mapper.extensionDescriptor(), mappedExt);
        }
    }
}
